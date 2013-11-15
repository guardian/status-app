jQuery ->
  $('#config').on('show.bs.modal', () ->
    $('#config input').map(() ->
      if (localStorage[this.value] == "off")
        this.checked = false
    )
  )

  $('#save-setttings').click((e) ->
    unchecked = $('#config input').map(() ->
      if (!this.checked)
        localStorage[this.value] = "off"
        $('#' + this.value).hide()
        console.log($('#' + this.value))
      else
        localStorage.removeItem(this.value)
    )
    $('#config').modal('hide')
  )

