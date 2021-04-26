resource "aws_autoscaling_group" "wonder-backend" {
  desired_capacity    = var.instance_count_desired
  max_size            = var.instance_count_max
  min_size            = var.instance_count_min
  vpc_zone_identifier = data.aws_subnet_ids.public.ids

  instance_refresh {
    strategy = "Rolling"
    preferences {
      instance_warmup        = 30
      min_healthy_percentage = 0
    }
    triggers = ["tag"]
  }

  tag {
    key                 = "terraform"
    value               = "true"
    propagate_at_launch = true
  }

  # Below is used to trigger the refresh immediately
  tag {
    key                 = "created"
    value               = local.current_time
    propagate_at_launch = true
  }

  mixed_instances_policy {
    instances_distribution {
      on_demand_base_capacity                  = 1
      on_demand_percentage_above_base_capacity = 0
      spot_allocation_strategy                 = "capacity-optimized"
    }

    launch_template {
      launch_template_specification {
        launch_template_id = aws_launch_template.wonder-backend.id
        version            = "$Latest"
      }

      override {
        instance_type     = var.instance_type
        weighted_capacity = "1"
      }

      override {
        instance_type     = "c5ad.large"
        weighted_capacity = "1"
      }
    }
  }
}