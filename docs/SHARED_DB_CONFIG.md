# Shared MariaDB Configuration (3 DOMservers + 6 Judgehosts)

A concise guide for running **1 shared MariaDB database** across **3 DOMservers** and **6 Judgehosts**, keeping all contest data (problems, test cases, submissions, judgements, and scoreboard) synchronized in real time.

> **Note:** Judgehosts never connect to MariaDB directly—they only communicate with a DOMserver over HTTP, and DOMjudge coordinates judging locks through the shared database.

---

## Recommended Topology

```text
                  ┌──► DOMserver 1 (PC 1) ◄── Judgehosts 1 & 2
[MariaDB :3306] ◄─┼──► DOMserver 2 (PC 2) ◄── Judgehosts 3 & 4
    (PC 1)        └──► DOMserver 3 (PC 3) ◄── Judgehosts 5 & 6
```

- **How teams connect:** Assign each lab/room to one DOMserver URL (e.g., Lab 1 → PC 1, Lab 2 → PC 2, Lab 3 → PC 3). This avoids reverse-proxy load balancers and prevents login session/cookie mismatches. If one DOMserver PC goes down, affected teams can simply switch to another DOMserver URL.

---

## Step 1: Expose MariaDB on PC 1 (Primary Server)

1. In `domjudge/docker-compose.yml` on **PC 1**, add `ports: - "3306:3306"` under `mariadb`:

   ```yaml
   mariadb:
     image: mariadb:10.11
     container_name: mariadb
     ports:
       - "3306:3306"
     environment:
       - MYSQL_ROOT_PASSWORD=rootpw
       - MYSQL_USER=domjudge
       - MYSQL_PASSWORD=djpw
       - MYSQL_DATABASE=domjudge
     command: --max-connections=1000 --max-allowed-packet=512M
     volumes:
       - mariadb-data:/var/lib/mysql
   ```

2. **VirtualBox NAT Port Forwarding (PC 1):** Add `TCP 3306 → 3306`.
3. **Windows Firewall (Admin PowerShell on PC 1):** Allow TCP `3306` **only** from PC 2 and PC 3 (never leave MariaDB open to contestant PCs):

   ```powershell
   New-NetFirewallRule -DisplayName "MariaDB for DOMservers" -Direction Inbound -LocalPort 3306 -Protocol TCP -RemoteAddress <PC2_IP>,<PC3_IP> -Action Allow
   ```

4. **Start PC 1 first:**
   ```bash
   docker compose up -d
   ```
   Wait until DOMserver 1 is completely up and you can log in. *(Never start all 3 DOMservers against an empty DB at the same time, or they will race to initialize the schema.)*

---

## Step 2: Configure DOMserver 2 & 3 (PC 2 & PC 3)

1. On **PC 2** and **PC 3**, remove the `mariadb` service and `depends_on` from `domjudge/docker-compose.yml`, and set `MYSQL_HOST` to **PC 1's Windows LAN IP**:

   ```yaml
   services:
     domserver:
       image: domjudge/domserver:8.3.1
       container_name: domserver
       ports:
         - "12345:80"
       volumes:
         - /sys/fs/cgroup:/sys/fs/cgroup:ro
       environment:
         - MYSQL_HOST=<PC1_WINDOWS_IP>
         - MYSQL_USER=domjudge
         - MYSQL_PASSWORD=djpw
         - MYSQL_DATABASE=domjudge
         - MYSQL_ROOT_PASSWORD=rootpw
         - CONTAINER_TIMEZONE=Asia/Colombo
   ```

2. Configure VirtualBox NAT Port Forwarding (`80` or `12345` → `12345`) and Windows Firewall (TCP `80,12345`) on PC 2 and PC 3, just like PC 1.
3. **Test DB connectivity** from PC 2 and PC 3 before starting:
   ```bash
   nc -zv <PC1_WINDOWS_IP> 3306
   ```
4. Start DOMserver 2 and DOMserver 3:
   ```bash
   docker compose up -d
   ```

---

## Step 3: Connect Your 6 Judgehosts

- Point **2 Judgehosts** to each DOMserver (`DOMSERVER_BASEURL=http://<PC1_IP>/`, `http://<PC2_IP>/`, `http://<PC3_IP>/`).
- Use the **same** `judgehost` username and password (the user table is shared in MariaDB).
- Give each Judgehost container a **unique `hostname`** (`judgedaemon-0` through `judgedaemon-5`) so they do not collide in DOMjudge's `judgehost` table.

---

## Verification Checklist

- **DB user access:** The `mariadb:10.11` image creates `domjudge@%` by default, allowing PC 2 and PC 3 to connect.
- **Sync & failover test:** Submit a solution on **DOMserver 2** and verify it immediately appears on **DOMserver 1** and **DOMserver 3**'s Jury UI and scoreboard.

