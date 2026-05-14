package com.task09.layer;

import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;

@LambdaLayer(
        layerName = "weather_sdk_layer",
        libraries = {"lib/gson-2.10.1.jar"},
        runtime = DeploymentRuntime.JAVA11,
        artifactExtension = ArtifactExtension.ZIP
)
public class SdkLayer {
}
