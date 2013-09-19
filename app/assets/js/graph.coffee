statsSpark = (containerId, data, formatSuffix = '') ->
  nv.addGraph(() ->
    chart = nv.models.sparklinePlus()

    chart
      .margin({left:0, right: 45, bottom: 10})
      .x((d,i) -> i)
      .xTickFormat((d) -> d3.time.format('%H:%M')(new Date(data[d].x)))
      .yTickFormat((d) -> d3.format('.0f')(d) + formatSuffix)

    d3.select(containerId)
      .datum(data)
      .transition().duration(250)
      .call(chart)

    chart
  )

statsBar = (containerId, data) ->
  nv.addGraph({
    generate: () ->
      containerParent = $(containerId).parent()
      nameContainter = $(containerId).prev()
      candidateWidth = containerParent.width() - nameContainter.width() - 50
      width = if (candidateWidth > 0) then candidateWidth else containerParent.width()
      $(containerId).width(width)
      chart = nv.models.historicalBar()
        .width(width)
        .height(containerParent.height())
        .color(() -> '#333333')

      d3.select(containerId)
        .datum(data)
        .transition()
        .call(chart)

      chart
    callback: (graph) ->
      graph.dispatch.on('elementMouseover', (e) ->
        left = e.e.pageX
        top = e.e.pageY

        content = "<p>#{d3.time.format('%H:%M')(new Date(e.point.x))} |  #{e.point.y}</p>"

        nv.tooltip.show([left, top], content, e.value < 0 ? 'n' : 's')
      )
      graph.dispatch.on('elementMouseout', (e) ->
        nv.tooltip.cleanup()
      )
  })

window.statsSpark = statsSpark
window.statsBar = statsBar