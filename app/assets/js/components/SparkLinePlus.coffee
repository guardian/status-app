{svg, g, path, circle, line, rect, text, div} = React.DOM

SparkLinePlus = React.createClass
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

  componentWillUnmount: () ->
    window.removeEventListener('resize', @deriveWidth)

  x: () ->
    d3.time.scale().range([0, @state.width - 50]).domain(d3.extent(@props.points, (d) -> d.x))

  y: () ->
    d3.scale.linear().range([@props.height,12]).domain(d3.extent(@props.points, (d) -> d.y))

window.SparkLinePlus = SparkLinePlus