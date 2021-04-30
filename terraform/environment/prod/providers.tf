terraform {
  backend "http" {
  }
  required_providers {
    gitlab = {
      source  = "gitlabhq/gitlab"
      version = "~> 3.1"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.0"
    }
  }
}

variable "gitlab_access_token" {
  type = string
}

provider "gitlab" {
  token = var.gitlab_access_token
}

data "gitlab_project" "wonder-web" {
  id = 25904105
}

# Configure the AWS Provider
provider "aws" {
  region     = "eu-central-1"
  access_key = var.aws_access_key_prod
  secret_key = var.aws_secret_key_prod
}