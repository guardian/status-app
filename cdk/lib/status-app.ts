import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import type { App } from "aws-cdk-lib";
import { CfnInclude } from "aws-cdk-lib/cloudformation-include";
import { join } from "path";

export class StatusApp extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);
    const yamlTemplateFilePath = join(__dirname, "../..", "cloud-formation/status-app-secure.yaml");
    new CfnInclude(this, "YamlTemplate", {
      templateFile: yamlTemplateFilePath,
    });
  }
}
