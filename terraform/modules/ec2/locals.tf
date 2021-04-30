locals {
  current_timestamp = timestamp()
  current_time      = formatdate("hh:mm:ss", local.current_timestamp)
}

output "current_time" {
  value = local.current_time
}