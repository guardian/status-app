jQuery ->

  reload = ->
    $('[data-ajax-refresh]').each ->
      $(this).map(() ->
        if localStorage[this.id] == "off"
          $(this).hide()
        else
          this
      ).filter(':visible').load($(this).data("ajax-refresh"))

  reload()

  unless (window.location.search.indexOf("no_refresh") != -1)
    setInterval(reload, interval)