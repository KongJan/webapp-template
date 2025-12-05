package com.cdkpatterns;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class LambdaHandlerTest {

  @DisplayName("Test APIGateway handler")
  @Test
  public void testHandleRequest() {
    DynamoDbClient dynamoDbClientMock = Mockito.mock(DynamoDbClient.class);
    Context context = Mockito.mock(Context.class);
    Mockito.when(context.getAwsRequestId()).thenReturn("ce67a098-01b6-4e37-a1a9-9f0ffe69e0d8");

    APIGatewayV2HTTPEvent input = new APIGatewayV2HTTPEvent();

    LambdaHandler lambdaHandler = new LambdaHandler(dynamoDbClientMock);
    APIGatewayV2HTTPResponse response = lambdaHandler.handleRequest(input, context);

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo("You have connected with the Lambda (- hits so far)");
    assertThat(response.getHeaders().get("Request-Id")).isEqualTo("ce67a098-01b6-4e37-a1a9-9f0ffe69e0d8");
  }
}
