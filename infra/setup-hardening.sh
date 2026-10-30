#!/usr/bin/env bash
# One-time hardening of the single VPS that runs both the site (k3s) and the development agents.
#   1. user.slice (agents, builds) gets a lower CPU weight and a memory ceiling, so a heavy build slows
#      itself down instead of the site.
#   2. The Kubernetes API (6443) and kubelet (10250) answer only on loopback and the pod network.
#      SSH, 80 and 443 are untouched.
#   3. fail2ban guards SSH.
# Idempotent; rollback: delete /etc/systemd/system/user.slice.d/50-dev-limits.conf and
# novelka-firewall.service, then `systemctl disable --now fail2ban`.
set -euo pipefail
[[ $EUID == 0 ]] || { echo "Usage: sudo bash $0" >&2; exit 2; }

install -d /etc/systemd/system/user.slice.d
cat > /etc/systemd/system/user.slice.d/50-dev-limits.conf <<'EOF'
[Slice]
CPUWeight=50
MemoryHigh=4G
MemoryMax=5G
EOF

cat > /etc/systemd/system/novelka-firewall.service <<'EOF'
[Unit]
Description=Close the Kubernetes API and kubelet ports to the outside
After=network-online.target
Wants=network-online.target
[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/sh -c 'for t in iptables ip6tables; do for r in "-i lo" "-i cni0" "-i flannel.1"; do $t -C INPUT -p tcp -m multiport --dports 6443,10250 $r -j ACCEPT 2>/dev/null || $t -I INPUT -p tcp -m multiport --dports 6443,10250 $r -j ACCEPT; done; $t -C INPUT -p tcp -m multiport --dports 6443,10250 -j DROP 2>/dev/null || $t -A INPUT -p tcp -m multiport --dports 6443,10250 -j DROP; done'
ExecStop=/bin/sh -c 'for t in iptables ip6tables; do for r in "-i lo" "-i cni0" "-i flannel.1"; do $t -D INPUT -p tcp -m multiport --dports 6443,10250 $r -j ACCEPT 2>/dev/null || true; done; $t -D INPUT -p tcp -m multiport --dports 6443,10250 -j DROP 2>/dev/null || true; done'
[Install]
WantedBy=multi-user.target
EOF

DEBIAN_FRONTEND=noninteractive apt-get install -y fail2ban >/dev/null
cat > /etc/fail2ban/jail.d/novelka.local <<'EOF'
[sshd]
enabled = true
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF

systemctl daemon-reload
systemctl enable --now novelka-firewall.service
systemctl enable fail2ban >/dev/null
systemctl restart fail2ban
echo 'Hardening applied: user.slice limits, firewall for 6443/10250, fail2ban for sshd.'
