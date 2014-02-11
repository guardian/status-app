{div, span, h4, svg, g, rect} = React.DOM
Queue = React.createClass
  render: () ->
    (div {className: 'row'}, [
      (h4 {className: 'col-md-3 queue-condensed'}, @props.name),
      (BarChart {
        points: @state.approxQueueLength
      })
    ])

  getInitialState: () -> {
    approxQueueLength: []
  }

  updateFromServer: () ->
    $.ajax({
      url: '/queue/' + @props.name
      contentType: "text/javascript"
      success: ((result) ->
        @setState(result)
      ).bind(this)
    })
    setTimeout(@updateFromServer, 5000)

  componentDidMount: () ->
    @updateFromServer()

BarChart = React.createClass
  render: () ->
    (div {
      className: 'col-md-9'
      onMouseLeave: @hideToolTip
    }, [
      (svg {
        className: 'queue-stats col-md-11'
        id: @props.name
        style: {
          overflow: 'visible'
        }
      }, [
        (g {className: 'nvd3  nv-wrap'}, [
          (g {className: 'nv-bars'},
            (rect {
              x: @x()(datum.x)
              y: 20 - @y()(datum.y)
              height: @y()(datum.y) + 1
              width: @rectWidth()
              onMouseEnter: @showToolTip.bind(this, datum)
            }) for datum in @props.points
          )
        ])
      ])
      (div {
        style: { textAlign: 'right' }
      }, if (@props.points.length > 0) then [d3.format(',')(@props.points[@props.points.length - 1].y)])
      if (@state.renderToolTip) then (div {
        style: {
          position: 'absolute'
          top: -30
          left: @x()(@state.hoverDatum.x)
          backgroundColor: '#FFFFFF'
          border: '1px solid grey'
          padding: '2px'
        }
      }, [d3.time.format('%H:%M')(new Date(@state.hoverDatum.x)) + ' | ' + d3.format(' ,')(@state.hoverDatum.y)])
    ])

  getInitialState: () -> {
    width: 10
    renderToolTip: false
  }

  rectWidth: () ->
    (@state.width / @props.points.length) - 1

  x: () ->
    d3.time.scale().range([0, @state.width - @rectWidth()]).domain(d3.extent(@props.points, (d) -> d.x))

  y: () ->
    d3.scale.linear().range([0,20]).domain([d3.min([d3.min(@props.points, (d) -> d.y), 0]), d3.max(@props.points, (d) -> d.y)])

  componentDidMount: () ->
    @setState({
      width: @getDOMNode().querySelector('svg').offsetWidth
    })

  showToolTip: (datum) ->
    @setState({
      width: @state.width
      renderToolTip: true
      hoverDatum: datum
    })

  hideToolTip: () ->
    @setState({
      renderToolTip: false
    })

window.Queue = Queue


