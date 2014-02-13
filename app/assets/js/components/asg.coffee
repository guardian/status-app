{div, span, h4, p, img, a, svg, g, path, circle, text, table, thead, tbody, th, tr, td, small, strong, button} = React.DOM

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
        elb: this.state.elb
      })
      (svg {
        id: this.props.name + "-stats"
        className: "sparkline stats"
      })
      (SparkLine { points: @state.averageCPU })
      (RecentActivity {
        asgName: this.props.name
        activities: this.state.recentActivity
      })
    ])

SparkLine = React.createClass
  render: () ->
    points = this.props.points
    if (points.length > 0)
      (svg { className: "sparkline stats"}, [
        (g { className: "nvd3 nv-wrap nv-sparklineplus"}, [
          (g { className: "nv-sparklineWrap" }, [
            (g { className: "nvd3 nv-wrap nv-sparkline"},
              [(path {
                d : "M#{@x()(points[0].x)},#{@y()(points[0].y)}" + ("L#{@x()(p.x)},#{@y()(p.y)}" for p in points).join('')
                stroke: "black"
              }, [])
              (circle {
                cx: @x()(points[points.length - 1].x)
                cy: @y()(points[points.length - 1].y)
                r: 2
                fill: "black"
              })]
            )
          (text {
            x: @state.width - 45
            y: @y()(points[points.length - 1].y) + 7
            style: {
              fontSize: '1em'
            }
          }, points[points.length - 1].y + "%")
          ])
        ])
      ])
    else
      (div {}, ["No data"])

  getInitialState: () -> {
    width: 10
  }

  deriveWidth: () ->
    @setState({
      width: @getDOMNode().offsetWidth
    })

  componentDidMount: () ->
    @deriveWidth()
    window.addEventListener('resize', @deriveWidth)

  x: () ->
    d3.time.scale().range([0, @state.width - 50]).domain(d3.extent(@props.points, (d) -> d.x))

  y: () ->
    d3.scale.linear().range([20,0]).domain(d3.extent(@props.points, (d) -> d.y))


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

window.AutoScalingGroup = AutoScalingGroup
