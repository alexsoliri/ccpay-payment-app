terraform {
  backend "azurerm" {}
}

data "terraform_remote_state" "core_apps_infrastructure" {
  backend = "azurerm"

  config {
    resource_group_name  = "mgmt-state-store-${var.subscription}"
    storage_account_name = "mgmtstatestore${var.subscription}"
    container_name       = "mgmtstatestorecontainer${var.env}"
    key                  = "core-infra/${var.env}/terraform.tfstate"
  }
}

data "terraform_remote_state" "shared_infra" {
  backend = "azurerm"

  config {
    resource_group_name  = "mgmt-state-store-${var.subscription}"
    storage_account_name = "mgmtstatestore${var.subscription}"
    container_name       = "mgmtstatestorecontainer${var.env}"
    key                  = "ccpay/${var.env}/terraform.tfstate"
  }
}
