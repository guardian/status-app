import 'source-map-support/register';
import { GuRoot } from '@guardian/cdk/lib/constructs/root';
import { StatusApp } from '../lib/status-app';

const app = new GuRoot();
new StatusApp(app, 'StatusApp-euwest-1-PROD', {
	stack: 'ophan',
	stage: 'PROD',
	env: { region: 'eu-west-1' },
});
