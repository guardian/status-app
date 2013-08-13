$ ->
  renderGroupStats = (g) ->
    "<tr><td>#{g.name}</td><td>#{g.fetch.formattedTime}</td>
     <td>#{g.query.formattedTime}</td><td>#{g.query.count}</td>
     <td>#{(g.query.time / g.query.count).toFixed(0)}</td></tr>"

  groupToObj = (grpName, grp) ->
    name: grpName
    query:
      count: grp.query_total
      time: grp.query_time_in_millis
      formattedTime: grp.query_time
    fetch:
      count: grp.fetch_total
      time: grp.fetch_time_in_millis
      formattedTime: grp.fetch_time

  dateToYMD = (date) ->
    d = date.getDate();
    m = date.getMonth() + 1;
    y = date.getFullYear();
    "#{y}-#{if (m<=9) then "0" + m else m}-#{if (d <= 9) then "0" + d else d}"

  refresh = ->
    $.getJSON "http://#{hostname}:9200/_all/_stats?groups=_all", (data) ->

      groups = (groupToObj(name, group) for name, group of data._all.total.search.groups)

      groups.sort (a,b) ->
        b.query.time - a.query.time

      tableBody = (renderGroupStats(g) for g in groups)

      html = "#{renderGroupStats(groupToObj("(overall)", data._all.total.search))} #{tableBody.join(" ")}"

      $("#stats-body").html(html)

  refresh()

  setInterval refresh, 30000
