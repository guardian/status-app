/** @jsx React.DOM */
var AutoScalingGroup = React.createClass({
    getInitialState: function() {
        return {
            members: [],
            recentActivity: []
        }
    },

    updateFromServer: function() {
        $.ajax({url: '/asg/' + this.props.name, contentType: "text/javascript", success: function(result) {
            this.setState({
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
                <table className="table table-condensed">
                    <thead>
                        <th>Instance</th>
                        <th>AutoScaling</th>
                        <th>Uptime</th>
                        <th>Version</th>
                    </thead>
                    <tbody>
                    {this.state.members.map(function(m) {
                        return <ClusterMember member={m} key={m.id} />
                    })}
                    </tbody>
                </table>
                <RecentActivity asgName={this.props.name} activities={this.state.recentActivity} />
            </div>
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
            <div id={this.props.asgName + "-activity"} className="accordion panel-footer">
                <div className="accordion-group">
                    <div className="accordion-heading">
                        <a className="accordion-toggle" onClick={this.toggle}>
                            <small>Recent activity</small>
                        </a>
                    </div>
                    {this.state.collapsed ? '' :
                    <div>
                        <div className="accordion-inner">
                            <small>
                            {this.props.activities.map(function(a) {
                                return <ScalingActivity age={a.age} cause={a.cause} />
                            })}
                            </small>
                        </div>
                    </div>
                    }
                </div>
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

