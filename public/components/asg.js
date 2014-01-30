/** @jsx React.DOM */
var AutoScalingGroup = React.createClass({
    getInitialState: function() {
        return {
            members: []
        }
    },

    updateFromServer: function() {
        $.ajax({url: '/asg/' + this.props.name, contentType: "text/javascript", success: function(result) {
            this.setState({
                members: result.members
            })}.bind(this)
        });
        setTimeout(this.updateFromServer, 5000)
    },

    componentDidMount: function() {
        this.updateFromServer()
    },

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
                {this.state.members.map(function(m) {
                    return <ClusterMember member={m} key={m.id}/>
                })}
                </tbody>
            </table>
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

