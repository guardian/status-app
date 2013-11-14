jQuery ->

  reload = ->
    $('[data-ajax-refresh]').each ->
      $(this).filter(':visible').load($(this).data("ajax-refresh"))

  reload()

  unless (window.location.search.indexOf("no_refresh") != -1)
    setInterval(reload, interval)