locals {
  script = templatefile("${path.module}/scripts/script.tpl", {
  })
}

output "script" {
  value = local.script
}

resource "aws_launch_template" "wonder-backend" {
  name_prefix            = "wonder-backend"
  image_id               = var.instance_ami
  instance_type          = var.instance_type
  vpc_security_group_ids = [data.aws_security_group.main.id]
  user_data              = base64encode(local.script)
  iam_instance_profile {
    name = data.aws_iam_instance_profile.wonder-core.name
  }

  tag_specifications {
    resource_type = "instance"

    tags = {
      Name        = "wonder-backend",
      terraform   = "true",
      environment = var.env_name
    }
  }
}