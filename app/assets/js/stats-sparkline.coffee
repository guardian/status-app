statsSpark = (containerId, data) ->
  nv.addGraph(() ->
    chart = nv.models.sparklinePlus()

    chart
      .margin({left:0, right: 45, bottom: 10})
      .x((d,i) -> i)
      .xTickFormat((d) -> d3.time.format('%H:%M')(new Date(data[d].x)))
      .yTickFormat((d) -> d3.format('.0f')(d) + '%')

    d3.select(containerId)
      .datum(data)
      .transition().duration(250)
      .call(chart)

    chart
  )

window.statsSpark = statsSpark