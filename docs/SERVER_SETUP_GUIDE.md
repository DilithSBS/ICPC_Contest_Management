# ICPC DOMjudge Server & Judgehost Setup Guide

A concise, verified runbook for setting up **1 DOMserver** and **1 Judgehost** as Ubuntu 22.04.5 LTS VirtualBox VMs on Windows desktops behind a campus LAN.

---

## 1. Architecture & Networking (NAT Mode)

Campus switches with MAC filtering/port security often block **Bridged** VMs from receiving an IP. Using **NAT mode** avoids this because all traffic uses the Windows host's MAC/IP:

```text
Contestants ─────────┐
                     ├──► http://<DOMSERVER_WINDOWS_IP>/ (or :12345) ──► Windows Host ──(NAT Forward)──► DOMserver VM
Judgehost VM (NAT) ──┘
```

### VirtualBox & Windows Host Settings
- **OS Image:** `Ubuntu Server 22.04.5 LTS` (64-bit, Minimal) with **OpenSSH Server** enabled. Do **not** install Snap Docker.
- **Windows Hosts (Both PCs):** Set Power Plan to **High Performance**, Sleep/Hibernate to **Never**, and pause Windows Updates.
- **DOMserver VM (VirtualBox → Network → NAT → Port Forwarding):**
  - `SSH`: `127.0.0.1:2222` → `22`
  - `DOMjudge`: `80` (or `12345`) → `12345` *(matches `12345:80` in `domjudge/docker-compose.yml`)*
  - `CDS` *(optional)*: `8443` → `8443`
  - **Windows Firewall (Admin PowerShell on DOMserver PC):**
    ```powershell
    New-NetFirewallRule -DisplayName "DOMjudge" -Direction Inbound -LocalPort 80,12345 -Protocol TCP -Action Allow
    ```
- **Judgehost VM (VirtualBox → Network → NAT):**
  - Outbound only; only `SSH` (`127.0.0.1:2222` → `22`) is needed.

---

## 2. OS Post-Install (Both VMs)

SSH into each VM (`ssh <user>@localhost -p 2222`) and run:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ca-certificates curl gnupg lsb-release git
sudo timedatectl set-timezone Asia/Colombo
sudo systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
```

On the **Judgehost VM only**, remove `apport` so crash reporting doesn't interfere with `RTE` detection:
```bash
sudo apt remove -y apport
```

---

## 3. Enable `cgroup v1` (Judgehost VM Only)

DOMjudge's `runguard` sandbox requires **cgroup v1** with memory/swap accounting.

1. Edit `/etc/default/grub`:
   ```bash
   sudo nano /etc/default/grub
   ```
   Set:
   ```text
   GRUB_CMDLINE_LINUX="systemd.unified_cgroup_hierarchy=0 cgroup_enable=memory swapaccount=1"
   ```
2. Apply and reboot:
   ```bash
   sudo update-grub && sudo reboot
   ```
3. Verify after reboot (directory must exist):
   ```bash
   ls /sys/fs/cgroup/memory
   ```

---

## 4. Install Official Docker Engine (Both VMs)

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
newgrp docker

docker run --rm hello-world
```

---

## 5. Deploy DOMserver (DOMserver VM)

1. Start MariaDB and DOMserver (`domjudge/domserver:8.3.1`):
   ```bash
   cd ~/ICPC_Contest_Management/domjudge
   docker compose up -d
   ```
2. Wait ~30–60s, then retrieve the initial `admin` password:
   ```bash
   docker compose exec domserver cat /opt/domjudge/domserver/etc/initial_admin_password.secret
   ```
3. From the **Judgehost VM**, verify LAN + NAT + Firewall reachability:
   ```bash
   curl -I http://<DOMSERVER_WINDOWS_IP>/   # or :12345
   ```
4. In the DOMjudge web UI (**Jury → Users → Add new user**), create user `judgehost` with roles **Jury** and **Judgehost**.

---

## 6. Configure & Start Judgehost (Judgehost VM)

1. Edit `judgehosts/docker-compose.yml` for both `judgehost-0` and `judgehost-1`:
   - Pin image to **`domjudge/judgehost:8.3.1`** (matches DOMserver).
   - Set `DOMSERVER_BASEURL=http://<DOMSERVER_WINDOWS_IP>/` (or `:12345/`).
   - Set `JUDGEDAEMON_PASSWORD=<your_judgehost_password>`.
   - **Remove `:ro`** from the cgroup volume so `create_cgroups` can write to `/sys/fs/cgroup`:
     ```yaml
     volumes:
       - /sys/fs/cgroup:/sys/fs/cgroup
     ```
     *(Quick fix: `sed -i 's|/sys/fs/cgroup:/sys/fs/cgroup:ro|/sys/fs/cgroup:/sys/fs/cgroup|g' docker-compose.yml`)*
2. Start and verify logs:
   ```bash
   docker compose up -d --force-recreate
   docker compose logs --tail=30
   ```
   Confirm logs show `[ok] cgroups set up`, `[ok] resolv.conf copied`, and `Judge started on judgedaemon-... [DOMjudge/8.3.1]`.

---

## 7. End-to-End Verification

1. Open **Jury → Judgehosts** in DOMjudge and confirm both daemons are **green / active**.
2. Create and activate a test contest, then import `example_question.zip` (**Jury → Import / export → Import problem**).
3. Submit `sol.cpp` / `sol.py` from `submissions/accepted/` and verify the verdict returns **`CORRECT`**.

