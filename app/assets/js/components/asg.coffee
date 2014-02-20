{div, span, h4, p, img, a, svg, g, path, circle, line, rect, text, table, thead, tbody, th, tr, td, small, strong, button} = React.DOM

Stage = React.createClass
  getInitialState: () -> {
    asgs: []
  }

  updateFromServer: () ->
    $.ajax({
      url: '/' + this.props.name
      contentType: "application/json"
      success: ((result) ->
        @setState({ asgs: result })
      ).bind(this)
    })
    setTimeout(this.updateFromServer, 5000)

  componentDidMount: () ->
    @updateFromServer()

  render: () ->
    (div {}, [(AutoScalingGroup { group: asg }) for asg in @state.asgs])

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
      })
      if this.props.group.elb && this.props.group.elb.active
        (SparkLine {
          points: @props.group.elb.latency
          unit: 'ms'
          height: 40
        })
      (ClusterMembers {
        members: this.props.group.members
        elb: this.state.elb
      })
      (SparkLine {
        points: @props.group.averageCPU
        unit: '%'
        height: 40
      })
      (RecentActivity {
        asgName: this.props.group.name
        activities: this.props.group.recentActivity
      })
    ])

SparkLine = React.createClass
  render: () ->
    points = this.props.points
    if (points.length > 0)
      (svg {
        className: "sparkline stats"

      }, [
        (g {
          className: "nvd3 nv-wrap nv-sparklineplus"
        }, [
          (rect {
            x: 0
            y: 0
            height: @props.height
            width: @state.width
            fill: 'white'
            onMouseMove: @calculateActivePoint
            onMouseLeave: @disableDetail
          })
          (g {
            className: "nv-sparklineWrap"
          }, [
            (g {
                className: "nvd3 nv-wrap nv-sparkline"
              },
              [(path {
                d : "M#{@x()(points[0].x)},#{@y()(points[0].y)}" + ("L#{@x()(p.x)},#{@y()(p.y)}" for p in points).join('')
                stroke: "black"
              }, [])
              (circle {
                cx: @x()(points[points.length - 1].x)
                cy: @y()(points[points.length - 1].y)
                r: 2
                fill: "black"
              })
              ]
            )
            (text {
              x: @state.width - 45
              y: @y()(points[points.length - 1].y) + 10
              style: {
                fontSize: '1em'
                fontWeight: 'normal'
              }
            }, points[points.length - 1].y + @props.unit)
          ])
        if @state.showDetail then (g { className: 'nv-hoverValue' }, [
          (line {
            x1: @x()(@state.detailPoint.x)
            x2: @x()(@state.detailPoint.x)
            y1: 0
            y2: @props.height
            stroke: 'black'
          })
          (text {
            x: @x()(@state.detailPoint.x) - 3
            y: 0
            style: {
              stroke: 'black'
              textAnchor: 'end'
              alignmentBaseline: 'hanging'
              strokeWidth: 0
            }
          }, d3.time.format('%H:%M')(new Date(@state.detailPoint.x)))
          (text {
            x: @x()(@state.detailPoint.x) + 3
            y: 0
            style: {
              stroke: 'black'
              textAnchor: 'start'
              alignmentBaseline: 'hanging'
              strokeWidth: 0
            }
          }, "" + d3.format(' ,')(@state.detailPoint.y) + @props.unit)
        ])
        ])
      ])
    else
      (div {}, ["No data"])

  getInitialState: () -> {
    width: 10
    showDetail: false
  }

  calculateActivePoint: (e) ->
    dt = @x().invert(e.clientX - @getDOMNode().getBoundingClientRect().left).getTime()
    lessThan = (point for point in @props.points when point.x < dt)
    biggestLessThan = lessThan[lessThan.length - 1]
    smallestGreaterThan = (point for point in @props.points when point.x > dt)[0]
    nearestPoint =
      if !smallestGreaterThan? || (biggestLessThan? && dt - biggestLessThan.x <= smallestGreaterThan.x)
        biggestLessThan
      else
        smallestGreaterThan
    @setState({
      detailPoint: nearestPoint
      showDetail: true
    })

  disableDetail: () -> @setState({ showDetail: false })

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
    d3.scale.linear().range([@props.height,10]).domain(d3.extent(@props.points, (d) -> d.y))


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
        this.props.appName
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

window.Stage = Stage
