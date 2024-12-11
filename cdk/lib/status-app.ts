import {GuEc2App} from '@guardian/cdk';
import {AccessScope} from '@guardian/cdk/lib/constants';
import type {GuStackProps} from '@guardian/cdk/lib/constructs/core';
import {GuStack, GuStringParameter} from '@guardian/cdk/lib/constructs/core';
import {GuDynamoTable} from '@guardian/cdk/lib/constructs/dynamodb';
import {GuAllowPolicy} from '@guardian/cdk/lib/constructs/iam';
import {type App, Duration, Tags} from 'aws-cdk-lib';
import {AttributeType, BillingMode} from 'aws-cdk-lib/aws-dynamodb';
import {InstanceClass, InstanceSize, InstanceType, Port, SecurityGroup, UserData,} from 'aws-cdk-lib/aws-ec2';
import {CfnRecordSet, RecordType} from 'aws-cdk-lib/aws-route53';

export class StatusApp extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { stage, stack } = props;

		const app = 'status-app';
		const region = 'eu-west-1';

		const hostedZoneName = new GuStringParameter(this, 'hosted-zone-name', {
			description:
				"DNS hosted zone for which A CNAME will be created. e.g. example.com (note, no trailing full-stop) for status.example.com. Leave empty if you don't want to add a CNAME to the status app",
		});

		const hostedZoneId = new GuStringParameter(this, 'hosted-zone-id', {
			description:
				"ID for the hosted zone",
		});

		const esTargetSecurityGroup = new GuStringParameter(this, 'elasticsearch-target-security-group', {
			description:
				"security group we want to direct to for the ElasticSearch instances",
		});

		const userData = UserData.custom(`#!/bin/bash -ev
          aws --region ${region} s3 cp s3://ophan-dist/ophan/${stage}/status-app/status-app_1.0_all.deb .
          dpkg -i status-app_1.0_all.deb
          /opt/cloudwatch-logs/configure-logs application ${stack} ${stage} status-app /var/log/status-app/status-app.log
         `);

		const domainName = `status.ophan.co.uk`;

		const ec2 = new GuEc2App(this, {
			app,
			access: {
				scope: AccessScope.PUBLIC,
			},
			instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
			applicationPort: 9000,
			monitoringConfiguration: { noMonitoring: true },
			scaling: { minimumInstances: 1, maximumInstances: 2 },
			certificateProps: { domainName: domainName, hostedZoneId: hostedZoneId.valueAsString },
			userData,
			imageRecipe: 'ophan-ubuntu-jammy-ARM-CDK',
			roleConfiguration: {
				additionalPolicies: [
					new GuAllowPolicy(this, 'dynamo-access', {
						resources: [
							`arn:aws:dynamodb:${region}:${this.account}:table/StatusAppConfig-${stage}`,
						],
						actions: ['dynamodb:GetItem'],
					}),
					new GuAllowPolicy(this, 'read-metadata', {
						resources: ['*'],
						actions: [
							'ec2:describe*',
							'autoscaling:Describe*',
							'elasticloadbalancing:Describe*',
							'cloudwatch:Get*',
							'sqs:ListQueues'
						]
					})
				],
			},
		});

		ec2.targetGroup.healthCheck = {
			...ec2.targetGroup.healthCheck,
			path: '/management/healthcheck',
			healthyThresholdCount: 2,
			unhealthyThresholdCount: 2,
			interval: Duration.seconds(10),
			timeout: Duration.seconds(5),
		};

		Tags.of(ec2.autoScalingGroup).add('SystemdUnit', `${app}.service`);

		const targetSecurityGroup = SecurityGroup.fromSecurityGroupId(
			this,
			'ElasticsearchSecurityGroup',
			esTargetSecurityGroup.valueAsString
		);

		const asgSecurityGroup = ec2.autoScalingGroup.connections.securityGroups[0];
		if (!asgSecurityGroup) {
			throw new Error('No security group found for auto scaling group');
		}

		asgSecurityGroup.addEgressRule(
			targetSecurityGroup,
			Port.tcp(9200),
			'Allow outbound traffic to Elasticsearch'
		);

		new GuDynamoTable(this, 'ConfigTable', {
			devXBackups: {
				enabled: true,
			},
			tableName: `StatusAppConfig-${stage}`,
			partitionKey: { name: 'key', type: AttributeType.STRING },
			billingMode: BillingMode.PROVISIONED,
			readCapacity: 1,
			writeCapacity: 1,
		});

		if (hostedZoneName.valueAsString) {
			new CfnRecordSet(this, "cname-record", {
				name:  `status.${hostedZoneName.valueAsString}`,
				comment: "CNAME for status app",
				type: RecordType.CNAME,
				hostedZoneName: `${hostedZoneName.valueAsString}.`,
				ttl: "900",
				resourceRecords: [
					ec2.loadBalancer.loadBalancerDnsName
				],
			})
		}
	}
}
