import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { StatusApp } from './status-app';

describe('The StatusApp stack', () => {
	it('matches the snapshot', () => {
		const app = new App();
		const stack = new StatusApp(app, 'StatusApp', {
			stack: 'ophan',
			stage: 'TEST',
		});
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});
