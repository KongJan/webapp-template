package com.cdkpatterns;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.apigatewayv2.CorsHttpMethod;
import software.amazon.awscdk.services.apigatewayv2.CorsPreflightOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.ICertificate;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.PriceClass;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

public class TheSimpleWebserviceStack extends Stack {

  public TheSimpleWebserviceStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);

    // constants
    var certificateArn = System.getenv("AWS_CERTIFICATE_ARN");
    var domainName = System.getenv("AWS_DOMAIN_NAME");

    // Create resources
    Bucket s3Bucket = createS3Bucket();
    Table dynamoDbTable = createDynamoDBTable();
    Function lambda = createLambda(dynamoDbTable.getTableName());

    // 2. Import an existing certificate (in us-east-1)
    ICertificate certificate = Certificate.fromCertificateArn(this, "SiteCert", certificateArn);

    Distribution distribution = createCloudfrontDistribution(s3Bucket, certificate, domainName);
    HttpApi api = createHttpApi(lambda, distribution);

    // 4. Route53 alias record
    IHostedZone hostedZone = HostedZone.fromLookup(
        this,
        "MyHostedZone",
        HostedZoneProviderProps.builder().domainName(domainName).build());

    ARecord.Builder.create(this, "AliasRecord")
        .zone(hostedZone)
        .recordName(domainName)
        .target(RecordTarget.fromAlias(new CloudFrontTarget(distribution)))
        .build();

    AaaaRecord.Builder.create(this, "AliasRecordAAAA")
        .zone(hostedZone)
        .recordName(domainName)
        .target(RecordTarget.fromAlias(new CloudFrontTarget(distribution)))
        .build();

    // bucket policy to allow distribution
    createBucketPolicyForDistribution(s3Bucket, distribution);

    // Deploy S3 bucket content
    deployS3BucketContent(s3Bucket, distribution);

    // Grant permissions
    dynamoDbTable.grantReadWriteData(lambda);

    // Outputs
    CfnOutput.Builder.create(this, "ApiUrl")
        .description("HTTP API Url")
        .value(api.getUrl())
        .build();

    CfnOutput.Builder.create(this, "CloudFrontDomain")
        .description("Cloudfront Domain")
        .value(distribution.getDistributionDomainName())
        .build();
  }

  private void createBucketPolicyForDistribution(Bucket bucket, Distribution distribution) {
    bucket.addToResourcePolicy(PolicyStatement.Builder.create()
        .sid("AllowCloudFrontServicePrincipal")
        .effect(Effect.ALLOW)
        .principals(List.of(new ServicePrincipal("cloudfront.amazonaws.com")))
        .actions(List.of("s3:GetObject"))
        .resources(List.of(bucket.getBucketArn() + "/*"))
        .conditions(Map.of(
            "StringEquals", Map.of(
                "AWS:SourceArn", distribution.getDistributionArn()
            )
        ))
        .build());
  }

  private void deployS3BucketContent(Bucket bucket, Distribution distribution) {
    BucketDeployment.Builder.create(this, "cdk-playground-s3-deployment")
        .sources(List.of(Source.asset("./webpage")))
        .destinationBucket(bucket)
        .distribution(distribution)
        .retainOnDelete(false)
        .prune(false)
        .build();
  }

  private HttpApi createHttpApi(Function dynamoLambda, Distribution distribution) {
    return HttpApi.Builder.create(this, "cdk-playground-api")
        .defaultIntegration(
            HttpLambdaIntegration.Builder
                .create("cdk-playground-lambda-integration", dynamoLambda)
                .build())
        .corsPreflight(CorsPreflightOptions.builder()
            .allowMethods(Collections.singletonList(CorsHttpMethod.GET))
            .allowOrigins(Collections.singletonList("https://" + distribution.getDistributionDomainName()))
            .build())
        .build();
  }

  private Function createLambda(String tableName) {
    return Function.Builder.create(this, "cdk-playground-lambda")
        .code(Code.fromAsset("./lambda/target/lambda.jar"))
        .handler("com.cdkpatterns.LambdaHandler::handleRequest")
        .runtime(Runtime.JAVA_21)
        .architecture(Architecture.ARM_64)
        .memorySize(1024)
        .environment(Map.of(
            "HITS_TABLE_NAME", tableName,
            "REGION", this.getRegion()))
        .build();
  }

  private Table createDynamoDBTable() {
    return new Table(this, "Hits", TableProps.builder()
        .partitionKey(Attribute.builder()
            .name("path")
            .type(AttributeType.STRING)
            .build())
        .build());
  }

  private Bucket createS3Bucket() {
    return Bucket.Builder.create(this, "cdk-playground-bucket")
        .versioned(true)
        .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
        .removalPolicy(RemovalPolicy.DESTROY)
        .encryption(BucketEncryption.S3_MANAGED)
        .autoDeleteObjects(true)
        .build();
  }

  private Distribution createCloudfrontDistribution(Bucket bucket, ICertificate cert,
      String domainName) {
    BehaviorOptions behaviorOptions = BehaviorOptions.builder()
        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
        .origin(S3BucketOrigin.withOriginAccessControl(bucket))
        .build();

    return Distribution.Builder
        .create(this, "cdk-playground-distribution")
        .priceClass(PriceClass.PRICE_CLASS_100)
        .defaultRootObject("index.html")
        .defaultBehavior(behaviorOptions)
        .domainNames(Collections.singletonList(domainName))
        .certificate(cert)
        .build();
  }
}
