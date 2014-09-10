{div, span, h4, p, img, a, table, thead, tbody, th, tr, td, small, strong, button, ul, li, noscript} = React.DOM
{Panel, Table} = ReactBootstrap

Stage = React.createClass
  getInitialState: () -> {
    stacks: null
    stackNames: []
    activeStack: null
  }

  updateFromServer: () ->
    $.ajax({
      url: "/#{@props.name}.json"
      success: ((result) ->
        stackNames = (name for name, _ of result)
        @setState({
          stacks: result
          stackNames: stackNames
        })
        if (@state.activeStack == null)
          @setState({ activeStack: stackNames[0] })
      ).bind(this)
    })
    setTimeout(@updateFromServer, 5000)

  componentDidMount: () ->
    for node in @getDOMNode().parentNode.childNodes when node != @getDOMNode()
      @getDOMNode().parentNode.removeChild(node)

    @updateFromServer()

  render: () ->
    (div {}, [
      if (@state.stackNames.length > 1)
        (div { className: 'col-xs-12'},
          (ul { className: 'nav nav-pills stacks' }, [
            (li { className: if (name == @state.activeStack ) then 'active' }, (a {
              href: '#'
              onClick: @markActive.bind(this, name)
            }, name )) for name in @state.stackNames
          ])
        )
      if (@state.activeStack) then (Stack { name: @state.activeStack, asgs: @state.stacks[@state.activeStack].asgs })
    ])

  markActive: (stackName) ->
    @setState({ activeStack: stackName })

Stack = React.createClass
  getInitialState: () -> {
    twoCol: false
  }

  componentDidMount: () ->
    @deriveCols()
    window.addEventListener('resize', @deriveCols)

  deriveCols: () ->
    @setState({
      twoCol: window.innerWidth > 767 and window.innerWidth < 1200
    })

  render: () ->
    asgs = @props.asgs
    chunkSize = (numChunks) ->
      asgs.length / numChunks
    if (@state.twoCol)
      (div {}, [
        (div { className: 'col-sm-6' }, [(AutoScalingGroup { group: asg }) for asg in @props.asgs.slice(0, chunkSize(2))])
        (div { className: 'col-sm-6' }, [(AutoScalingGroup { group: asg }) for asg in @props.asgs.slice(chunkSize(2))])
      ])
    else
      (div {}, [
        (div { className: 'col-lg-4' }, [(AutoScalingGroup { group: asg }) for asg in @props.asgs.slice(0, chunkSize(3))])
        (div { className: 'col-lg-4' }, [(AutoScalingGroup { group: asg }) for asg in @props.asgs.slice(chunkSize(3), 2 * chunkSize(3))])
        (div { className: 'col-lg-4' }, [(AutoScalingGroup { group: asg }) for asg in @props.asgs.slice(2 * chunkSize(3))])
      ])

AutoScalingGroup = React.createClass
  getInitialState: () -> {
    app: "",
    members: [],
    recentActivity: []
  }

  render: () ->
    group = @props.group
    (Panel {
      className: "asg"
      header: (ClusterTitle {
        name: group.name
        app: group.app
        approxMonthlyCost: group.approxMonthlyCost
        moreDetailsLink: group.moreDetailsLink
      })
      footer: if group.recentActivity.length > 0
        (RecentActivity {
          asgName: group.name
          activities: group.recentActivity
        })
    }, [
      if group.suspendedActivities?.length > 0
        (div { className: 'alert-info' }, [
          (strong {}, "Suspended activities")
          ": #{group.suspendedActivities.join(',')}"
        ])
      if group.elb && group.elb.active
        (a {
          href: "https://console.aws.amazon.com/cloudwatch/home?region=eu-west-1#metrics:graph=!D05!E07!ET6!MN4!NS2!PD1!SS3!ST0!VA-PT3H~60~AWS%25252FELB~Average~Latency~LoadBalancerName~P0D~#{group.elb.name}"
        }, [
          (SparklinePlus {
            points: {x: point.time, y: point.average} for point in group.elb.latency
            unit: 'ms'
            height: 50
            additionalLine:
              (Sparkline {
                points: {x: point.time, y: point.sum} for point in group.elb.errorCount
                stroke: 'red'
              })
          })
        ])
      (ClusterMembers {
        members: group.members
        elb: group.elb
      })
      (a {
        href: "https://console.aws.amazon.com/cloudwatch/home?region=eu-west-1#metrics:graph=!D03!E06!ET7!MN5!NS2!PD1!SS4!ST0!VA-PT3H~60~AWS%252FEC2~AutoScalingGroupName~Maximum~CPUUtilization~#{group.name}~P0D"
      }, [
        (SparklinePlus {
          points: {x: point.time, y: point.maximum} for point in group.cpu
          unit: '%'
          height: 50
        })
      ])
    ])

ClusterTitle = React.createClass
  render: () ->
    (h4 { className: "panel-title" }, [
      (div { className: "pull-right" }, [
        (button {
          id: @props.name + "-copy"
          'data-clipboard-text': @props.name
          title: "Copy ASG name to clipboard"
        }, [
          (img {
            src: "/assets/images/ios7-copy-outline.png"
            height: "16px"
          })
        ])
      ]),
      if @props.moreDetailsLink? then (a { href: @props.moreDetailsLink }, @props.app) else @props.app
      (small {}, " (~$#{d3.format(',.0f')(@props.approxMonthlyCost)}/month)")
    ])

  componentDidMount: () ->
    new ZeroClipboard(document.getElementById(@props.name + "-copy"))

ClusterMembers = React.createClass
  render: () ->
    hasELB = @props.elb?
    (Table { condensed: true }, [
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
        @props.members.map((m) -> (ClusterMember {
          member: m
          key: m.id
          hasELB: hasELB
          url: m.url
        }))
      ])
    ])

RecentActivity = React.createClass
  toggle: () ->
      @setState({ collapsed: !@state.collapsed })

  getInitialState: () -> {
    collapsed: true
  }

  render: () ->
    (div {
      id: @props.asgName + "-activity"
    }, [
      (a { onClick: @toggle }, [
        (small {}, "Recent activity")
      ])
      if (!@state.collapsed)
        (div {}, [
          (small {}, [
            @props.activities.map((a) ->
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
      (strong {}, [@props.age])
      @props.cause
    ])

ClusterMember = React.createClass
  render: () ->
    member = @props.member
    (tr { className: member.goodorbad }, [
      (td {}, [
        (a { href: "/instance/#{member.id}" }, [member.id])
      ])
      (td {}, member.lifecycleState )
      if (@props.hasELB) then (td { title: member.description }, member.state )
      (td {}, member.uptime)
      (td {}, member.version)
    ])

window.Stage = Stage
