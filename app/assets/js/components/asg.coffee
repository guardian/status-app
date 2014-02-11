{div, span, h4, p, img, a, svg, g, table, thead, tbody, th, tr, td, small, strong, button} = React.DOM

AutoScalingGroup = React.createClass
  getInitialState: () -> {
    appName: "",
    members: [],
    recentActivity: [],
    averageCPU: []
  }

  updateFromServer: () ->
    $.ajax({
      url: '/asg/' + this.props.name
      contentType: "text/javascript"
      success: ((result) ->
        @setState(result)
      ).bind(this)
    })
    setTimeout(this.updateFromServer, 5000)

  componentDidMount: () ->
    this.updateFromServer()

  componentDidUpdate: () ->
    statsSpark('#' + this.props.name + '-stats',
      this.state.averageCPU,
      '%')
    if (this.state.elb && this.state.elb.active)
      statsSpark('#' + this.state.elb.name + '-stats',
        this.state.elb.latency,
        'ms')

  render: () ->
    (div {}, [
      (ClusterTitle {
        name: this.props.name
        appName: this.state.appName
      })
      if this.state.elb && this.state.elb.active
        (svg {
          id: this.state.elb.name + "-stats"
          className: "sparkline stats"
        })
      (ClusterMembers {
        members: this.state.members
      })
      (svg {
        id: this.props.name + "-stats"
        className: "sparkline stats"
      })
      (RecentActivity {
        asgName: this.props.name
        activities: this.state.recentActivity
      })
    ])

ClusterTitle = React.createClass
  render: () ->
    (div { className: "panel-heading" }, [
      (h4 { className: "panel-title" }, [
        (div { className: "pull-right" }, [
          (button {
            id: this.props.name + "-copy"
            'data-clipboard-text': this.props.name
            className: "clipboard"
            title: "Copy ASG name to clipboard"
          }, [
            (img {
              src: "/assets/images/ios7-copy-outline.png"
              height: "16px"
            })
          ])
        ])
        this.props.appName
      ])
    ])

  componentDidMount: () ->
    new ZeroClipboard(document.getElementById(this.props.name + "-copy"))

ClusterMembers = React.createClass
  render: () ->
    (table { className: "table table-condensed" }, [
      (thead {}, [
        (th {}, ["Instance"])
        (th {}, ["AutoScaling"])
        (th {}, ["Uptime"])
        (th {}, ["Version"])
      ])
      (tbody {}, [
        this.props.members.map((m) -> (ClusterMember {
          member: m
          key: m.id
        }))
      ])
    ])

RecentActivity = React.createClass
  toggle: () ->
      this.setState({ collapsed: !this.state.collapsed })

  getInitialState: () -> {
    collapsed: true
  }

  render: () ->
    if (this.props.activities.length == 0) then (div {})
    else
      (div {
        id: this.props.asgName + "-activity"
        className: "panel-footer"
      }, [
        (a { onClick: this.toggle }, [
          (small {}, "Recent activity")
        ])
        if (!this.state.collapsed)
          (div {}, [
            (small {}, [
              this.props.activities.map((a) ->
                (ScalingActivity {
                  age: a.age
                  cause: a.cause
                })
              )
            ])
          ])
      ])


ScalingActivity = React.createClass
  render: () ->
    (p {}, [
      (strong {}, [this.props.age])
      this.props.cause
    ])

ClusterMember = React.createClass
  render: () ->
    (tr { className: this.props.member.goodorbad }, [
      (td {}, [
        (a { href: this.props.url }, [this.props.member.id])
      ])
      (td {}, this.props.member.lifecycleState )
      (td {}, this.props.member.uptime)
      (td {}, this.props.member.version)
    ])

window.AutoScalingGroup = AutoScalingGroup
