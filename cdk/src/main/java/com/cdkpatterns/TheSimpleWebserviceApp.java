package com.cdkpatterns;

import java.util.Map;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;


public class TheSimpleWebserviceApp {
  public static void main(final String[] args) {

    App app = new App();

    var awsAccount = System.getenv("AWS_DEFAULT_ACCOUNT");
    var awsRegion = System.getenv("AWS_DEFAULT_REGION");

    var stackProps = StackProps
        .builder()
        .stackName("cdk-playground-stack")
        .env(Environment
            .builder()
            .account(awsAccount)
            .region(awsRegion)
            .build())
        .analyticsReporting(true)
        .terminationProtection(false)
        .description("cdk-playground-stack")
        .tags(Map.of("project", "cdk-playground"))
        .build();

    new TheSimpleWebserviceStack(app, "cdk-playground-stack", stackProps);

    app.synth();
  }
}
