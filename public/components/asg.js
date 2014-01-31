/** @jsx React.DOM */
var AutoScalingGroup = React.createClass({
    getInitialState: function() {
        return {
            appName: "",
            members: [],
            recentActivity: []
        }
    },

    updateFromServer: function() {
        $.ajax({url: '/asg/' + this.props.name, contentType: "text/javascript", success: function(result) {
            this.setState({
                appName: result.appName,
                members: result.members,
                recentActivity: result.recentActivity
            })}.bind(this)
        });
        setTimeout(this.updateFromServer, 5000)
    },

    componentDidMount: function() {
        this.updateFromServer()
    },

    render: function() {
        return (
            <div>
                <ClusterTitle name={this.props.name} appName={this.state.appName} />
                <ClusterMembers members={this.state.members} />
                <RecentActivity asgName={this.props.name} activities={this.state.recentActivity} />
            </div>
        );
    }
});

var ClusterTitle = React.createClass({
    render: function() {
        return(
            <div className="panel-heading">
                <h4 className="panel-title">
                    <div className="pull-right">
                        <button id={this.props.name + "-copy"} data-clipboard-text={this.props.name}
                        className="clipboard" title="Copy ASG name to clipboard">
                            <img src="/assets/images/ios7-copy-outline.png" height="16px"/>
                        </button>
                    </div>
                    {this.props.appName}
                </h4>
            </div>
        )
    },
    componentDidMount: function() {
        new ZeroClipboard(document.getElementById(this.props.name + "-copy"))
    }
});

var ClusterMembers = React.createClass({
    render: function() {
        return (
            <table className="table table-condensed">
                <thead>
                    <th>Instance</th>
                    <th>AutoScaling</th>
                    <th>Uptime</th>
                    <th>Version</th>
                </thead>
                <tbody>
                {this.props.members.map(function(m) {
                    return <ClusterMember member={m} key={m.id} />
                })}
                </tbody>
            </table>
         );
    }
});

var RecentActivity = React.createClass({

    toggle: function() {
        this.setState({collapsed: !this.state.collapsed})
    },

    getInitialState: function() {
        return {
            collapsed: true
        }
    },

    render: function() {

        if (this.props.activities.length === 0) {
            return (<div></div>)
        }
        return (
            <div id={this.props.asgName + "-activity"} className="panel-footer">
                <div>
                    <a onClick={this.toggle}>
                        <small>Recent activity</small>
                    </a>
                </div>
                {this.state.collapsed ? '' :
                <div>
                    <small>
                    {this.props.activities.map(function(a) {
                        return <ScalingActivity age={a.age} cause={a.cause} />
                    })}
                    </small>
                </div>
                }
            </div>
        )
    }
});

var ScalingActivity = React.createClass({
    render: function() {
        return (
            <p><strong>{this.props.age}</strong>{this.props.cause}</p>
        );
    }
});

var ClusterMember = React.createClass({
    render: function() {
        return (
            <tr className={this.props.member.goodorbad}>
                <td><a href={this.props.url}>{this.props.member.id}</a></td>
                <td>{this.props.member.lifecycleState}</td>

                <td>{this.props.member.uptime}</td>
                <td>{this.props.member.version}</td>
            </tr>
        );
    }
});

