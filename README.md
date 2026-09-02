# Kfpcl_Backend2 (Anusha Bazaar Backend)

## Overview
This is the Java Spring Boot backend for the Anusha Bazaar E-commerce platform.

## CI/CD Pipeline
The project is configured with a fully automated CI/CD pipeline:
- **CI**: GitHub Actions builds the project using Maven and creates a Docker image.
- **Registry**: Docker images are stored in Amazon ECR.
- **CD**: GitHub Actions deploys the latest container to Amazon EC2 via SSH.

## Setup
- **Java**: 17+
- **Port**: 9000
- **Database**: AWS RDS MySQL
- **Storage**: AWS S3

---
*Deployment initiated on: 2026-03-28*
