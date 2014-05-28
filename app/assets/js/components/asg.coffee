{div, span, h4, p, img, a, table, thead, tbody, th, tr, td, small, strong, button, ul, li} = React.DOM

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
    (div {
      className: "panel panel-default asg"
      style: {
        overflow: 'hidden'
      }
    }, [
      (ClusterTitle {
        name: @props.group.name
        app: @props.group.app
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
            points: {x: point.time, y: point.average} for point in @props.group.elb.latency
            unit: 'ms'
            height: 50
            additionalLine:
              (Sparkline {
                points: {x: point.time, y: point.sum} for point in @props.group.elb.errorCount
                stroke: 'red'
              })
          })
        ])
      (ClusterMembers {
        members: @props.group.members
        elb: @props.group.elb
      })
      (a {
        href: "https://console.aws.amazon.com/cloudwatch/home?region=eu-west-1#metrics:graph=!D03!E06!ET7!MN5!NS2!PD1!SS4!ST0!VA-PT3H~60~AWS%252FEC2~AutoScalingGroupName~Maximum~CPUUtilization~#{@props.group.name}~P0D"
      }, [
        (SparklinePlus {
          points: {x: point.time, y: point.maximum} for point in @props.group.cpu
          unit: '%'
          height: 50
        })
      ])
      (RecentActivity {
        asgName: @props.group.name
        activities: @props.group.recentActivity
      })
    ])

ClusterTitle = React.createClass
  render: () ->
    (div { className: "panel-heading" }, [
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
        ])

        if @props.moreDetailsLink? then (a { href: @props.moreDetailsLink }, @props.app) else @props.app
        (small {}, " (~$#{d3.format(',.0f')(@props.approxMonthlyCost)}/month)")
      ])
    ])

  componentDidMount: () ->
    new ZeroClipboard(document.getElementById(@props.name + "-copy"))

ClusterMembers = React.createClass
  render: () ->
    hasELB = @props.elb?
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
    if (@props.activities.length == 0) then (div {})
    else
      (div {
        id: @props.asgName + "-activity"
        className: "panel-footer"
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
    (React.DOM.p {}, [
      (strong {}, [@props.age])
      @props.cause
    ])

ClusterMember = React.createClass
  render: () ->
    hasELB = @props.hasELB
    (tr { className: @props.member.goodorbad }, [
      (td {}, [
        (a { href: "/instance/#{@props.member.id}" }, [@props.member.id])
      ])
      (td {}, @props.member.lifecycleState )
      if (hasELB) then (td { title: @props.member.description }, @props.member.state )
      (td {}, @props.member.uptime)
      (td {}, @props.member.version)
    ])

window.Stage = Stage
