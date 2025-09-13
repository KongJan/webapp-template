package com.cdkpatterns;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;


public class TheSimpleWebserviceApp {
  public static void main(final String[] args) {

    App app = new App();
    Tags.of(app).add("project", "cdk-playground");

    var stackProps = StackProps
        .builder()
        .stackName("cdk-playground-stack")
        .env(Environment
            .builder()
            // .account(awsAccount)
            // .region(awsRegion)
            .build())
        .analyticsReporting(true)
        .terminationProtection(false)
        .description("cdk-playground-stack")
        .build();

    new TheSimpleWebserviceStack(app, "cdk-playground-stack", stackProps);

    app.synth();
  }
}
