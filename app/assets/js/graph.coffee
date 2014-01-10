statsSpark = (containerSelector, data, formatSuffix = '') ->
  nv.addGraph(() ->
    chart = nv.models.sparklinePlus()

    chart
      .margin({left:0, right: 50, bottom: 10})
      .x((d,i) -> i)
      .xTickFormat((d) -> d3.time.format('%H:%M')(new Date(data[d].x)))
      .yTickFormat((d) -> d3.format('.0f')(d) + formatSuffix)

    d3.selectAll(containerSelector).filter(() -> this.offsetHeight != 0)
      .datum(data)
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
        .margin({right: 45})
        .color(() -> '#333333')

      d3.select(containerId)
        .datum(data)
        .transition()
        .call(chart)

      maxValue = d3.max(data[0].values, (val) -> val.y)

      d3.select(containerId)
        .append('g')
        .attr('transform', "translate(#{width - 35}, 0)")
        .append('text').attr('dy', '.8em').text(maxValue)

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