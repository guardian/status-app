$ ->
  deploy = (clusterName, buildNumber, srczip) ->
    console?.log "Opening socket...."

    infoSocket = new WebSocket("ws://#{window.location.host}/deploy/websocket?clusterName=#{clusterName}&version=#{buildNumber}")

    infoSocket.onerror = (event) ->
      $('#deploy-results').append("Socket error: #{event.data}<br>")
      console?.log event.data

    infoSocket.onmessage = (event) ->
      data = event.data
      $('#deploy-results').append("#{data}<br>")

    infoSocket.onopen = ->
      # this is mainly to stop accidental deploys by curling the url -
      # the deploy won't actually start until it's get the src url
      console?.log "Socket opened"
      infoSocket.send srczip


  # export
  window.deploy = deploy