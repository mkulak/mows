#!/bin/bash

# Add SSH key
cat <<'EOF' > /home/ec2-user/.ssh/authorized_keys
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJ4c9GoBSHO5BgcXvVa4wcLa+Yjf6E4DAwji4PL/xnT5 gitlab-runner
EOF

# Setup SSL proxy
#wget -qO- "https://getbin.io/suyashkumar/ssl-proxy" | tar xvz
#aws s3 sync s3://wonder-dev-certs certs
#nohup ./ssl-proxy-linux-amd64 -cert certs/test.wonder.ac.cer -key certs/test.wonder.ac.key -from 0.0.0.0:443 -to 127.0.0.1:8080 &