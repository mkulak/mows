module "ec2" {
  source                 = "../../modules/ec2"
  env_name               = "prod"
  instance_ami           = ""
  instance_count_desired = ""
  instance_count_max     = ""
  instance_count_min     = ""
  instance_type          = ""
}