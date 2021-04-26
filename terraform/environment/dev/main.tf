module "ec2" {
  source                 = "../../modules/ec2"
  env_name               = "dev"
  instance_ami           = "ami-0a6dc7529cd559185" # Amazon Linux 2 AMI (HVM)
  instance_count_desired = "2"
  instance_count_max     = "4"
  instance_count_min     = "1"
  instance_type          = "c5a.large"
}