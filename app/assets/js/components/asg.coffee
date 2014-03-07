{div, span, h4, p, img, a, table, thead, tbody, th, tr, td, small, strong, button} = React.DOM

Stage = React.createClass
  getInitialState: () -> {
    asgs: []
  }

  updateFromServer: () ->
    $.ajax({
      url: "/#{this.props.name}.json"
      success: ((result) ->
        @setState({ asgs: result })
      ).bind(this)
    })
    setTimeout(this.updateFromServer, 5000)

  componentDidMount: () ->
    for node in @getDOMNode().parentNode.childNodes when node != @getDOMNode()
      @getDOMNode().parentNode.removeChild(node)

    @updateFromServer()
    @deriveCols()
    window.addEventListener('resize', @deriveCols)

  deriveCols: () ->
    @setState({
      twoCol: window.innerWidth > 767 and window.innerWidth < 1200
    })

  render: () ->
    asgs = @state.asgs
    chunkSize = (numChunks) ->
      asgs.length / numChunks
    if (@state.twoCol)
      (div {}, [
        (div { className: 'col-sm-6' }, [(AutoScalingGroup { group: asg }) for asg in @state.asgs.slice(0, chunkSize(2))])
        (div { className: 'col-sm-6' }, [(AutoScalingGroup { group: asg }) for asg in @state.asgs.slice(chunkSize(2))])
      ])
    else
      (div {}, [
        (div { className: 'col-lg-4' }, [(AutoScalingGroup { group: asg }) for asg in @state.asgs.slice(0, chunkSize(3))])
        (div { className: 'col-lg-4' }, [(AutoScalingGroup { group: asg }) for asg in @state.asgs.slice(chunkSize(3), 2 * chunkSize(3))])
        (div { className: 'col-lg-4' }, [(AutoScalingGroup { group: asg }) for asg in @state.asgs.slice(2 * chunkSize(3))])
      ])

AutoScalingGroup = React.createClass
  getInitialState: () -> {
    appName: "",
    members: [],
    recentActivity: [],
    averageCPU: []
  }

  render: () ->
    (div {
      className: "panel panel-default asg"
      style: {
        overflow: 'hidden'
      }
    }, [
      (ClusterTitle {
        name: this.props.group.name
        appName: this.props.group.appName
        approxMonthlyCost: @props.group.approxMonthlyCost
        moreDetailsLink: @props.group.moreDetailsLink
      })
      if @props.group.suspendedActivities?.length > 0
        (div { className: 'panel-body alert-info' }, [
          (strong {}, "Suspended activities")
          ": #{@props.group.suspendedActivities.join(',')}"
        ])
      if @props.group.elb && @props.group.elb.active
        (a {
          href: "https://console.aws.amazon.com/cloudwatch/home?region=eu-west-1#metrics:graph=!D05!E07!ET6!MN4!NS2!PD1!SS3!ST0!VA-PT3H~60~AWS%25252FELB~Average~Latency~LoadBalancerName~P0D~#{@props.group.elb.name}"
        }, [
          (SparklinePlus {
            points: @props.group.elb.latency
            unit: 'ms'
            height: 50
          })
        ])
      (ClusterMembers {
        members: this.props.group.members
        elb: this.props.group.elb
      })
      (a {
        href: "https://console.aws.amazon.com/cloudwatch/home?region=eu-west-1#metrics:graph=!D03!E06!ET7!MN5!NS2!PD1!SS4!ST0!VA-PT3H~60~AWS%252FEC2~AutoScalingGroupName~Average~CPUUtilization~#{@props.group.name}~P0D"
      }, [
        (SparklinePlus {
          points: @props.group.averageCPU
          unit: '%'
          height: 50
        })
      ])
      (RecentActivity {
        asgName: this.props.group.name
        activities: this.props.group.recentActivity
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
            title: "Copy ASG name to clipboard"
          }, [
            (img {
              src: "/assets/images/ios7-copy-outline.png"
              height: "16px"
            })
          ])
        ])
        if @props.moreDetailsLink? then (a { href: @props.moreDetailsLink }, @props.appName) else @props.appName
        (small {}, " (~$#{d3.format(',.0f')(@props.approxMonthlyCost)}/month)")
      ])
    ])

  componentDidMount: () ->
    new ZeroClipboard(document.getElementById(this.props.name + "-copy"))

ClusterMembers = React.createClass
  render: () ->
    hasELB = this.props.elb?
    (table { className: "table table-condensed" }, [
      (thead {}, [
        (tr {}, [
          (th {}, ["Instance"])
          (th {}, ["AutoScaling"])
          if (hasELB)
            (th {}, ["ELB"])
          else

          (th {}, ["Uptime"])
          (th {}, ["Version"])
        ])
      ])
      (tbody {}, [
        this.props.members.map((m) -> (ClusterMember {
          member: m
          key: m.id
          hasELB: hasELB
          url: m.url
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
    (React.DOM.p {}, [
      (strong {}, [this.props.age])
      this.props.cause
    ])

ClusterMember = React.createClass
  render: () ->
    hasELB = this.props.hasELB
    (tr { className: this.props.member.goodorbad }, [
      (td {}, [
        (a { href: this.props.url }, [this.props.member.id])
      ])
      (td {}, this.props.member.lifecycleState )
      if (hasELB) then (td { title: this.props.member.description }, this.props.member.state )
      (td {}, this.props.member.uptime)
      (td {}, this.props.member.version)
    ])

window.Stage = Stage
