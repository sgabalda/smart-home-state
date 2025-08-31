# Raspberry Pi Deployment Guide

This guide will help you set up your Raspberry Pi to automatically deploy and update the smart-home-state application using Docker and Watchtower.

## Prerequisites

- Raspberry Pi 3B+ (1GB RAM) or newer
- Raspberry Pi OS (64-bit preferred for better Docker performance)
- Internet connection
- SSH access to your Pi
- OpenHAB API token
- MQTT broker details

## Step 1: Install Docker on Raspberry Pi

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add your user to docker group (to run without sudo)
sudo usermod -aG docker $USER

# Install Docker Compose
sudo apt install docker-compose -y

# Enable Docker service
sudo systemctl enable docker
sudo systemctl start docker

# Reboot to apply group changes
sudo reboot
```

After reboot, verify Docker is working:

```bash
docker --version
docker-compose --version
```

## Step 2: Set up the Application

1. **Create application directory:**

```bash
mkdir -p ~/smart-home-state
cd ~/smart-home-state
```

2. **Download docker-compose.yml:**

```bash
wget https://raw.githubusercontent.com/sgabalda/smart-home-state/main/docker-compose.yml
```

3. **Create data directories:**

```bash
mkdir -p data logs
```

4. **Configure environment variables (IMPORTANT for security):**

```bash
# Copy the template and edit with your secure values
cp .env.template .env
nano .env
```

**Required configuration in .env:**

```bash
# MQTT Configuration
MQTT_BROKER_HOST=your-mqtt-broker
MQTT_CLIENT_ID=smart-home-state

# OpenHAB Configuration
OPENHAB_HOST=your-openhab-server
OPENHAB_API_TOKEN=your_secret_api_token_here

# Email notifications for updates (optional but recommended)
NOTIFICATION_EMAIL_FROM=your-email@gmail.com
NOTIFICATION_EMAIL_TO=your-email@gmail.com
NOTIFICATION_EMAIL_USER=your-email@gmail.com
NOTIFICATION_EMAIL_PASSWORD=your-gmail-app-password
NOTIFICATION_EMAIL_SUBJECT_TAG=[Smart Home]

# Optional: Adjust log level
STATE_PERSISTENCE_INTERVAL=10 seconds
```

### Setting up Gmail App Password

To use Gmail for notifications, you need to create an App Password:

1. **Enable 2-Step Verification:**

   - Go to [Google Account Security](https://myaccount.google.com/security)
   - Under "Signing in to Google", enable "2-Step Verification" if not already enabled

2. **Generate App Password:**
   - Go to [App passwords](https://myaccount.google.com/apppasswords)
   - Select "Mail" as the app
   - Copy the generated 16-character password (without spaces)
   - Use this as your `NOTIFICATION_EMAIL_PASSWORD`

**Security Note:** Never commit the `.env` file to version control. It contains sensitive credentials.

## Step 3: Initial Deployment

1. **Start the application:**

```bash
docker-compose up -d
```

2. **Check logs:**

```bash
# View application logs
docker-compose logs -f smart-home-state

# View watchtower logs
docker-compose logs -f watchtower
```

3. **Verify containers are running:**

```bash
docker-compose ps
```

## Step 4: Configure Automatic Updates

Watchtower is already configured in the docker-compose.yml to:

- Check for new images every 5 minutes
- Automatically pull and restart with new versions
- Clean up old images to save space
- Only update containers with the `watchtower.enable` label

## Step 5: Performance Optimization for Pi 3B+

The Pi 3B+ has limited resources (1GB RAM), so consider these optimizations:

### Memory Management

```bash
# Monitor memory usage
free -h
docker stats

# If needed, increase swap
sudo dphys-swapfile swapoff
sudo nano /etc/dphys-swapfile  # Set CONF_SWAPSIZE=1024
sudo dphys-swapfile setup
sudo dphys-swapfile swapon
```

### Reduce Watchtower Check Frequency

If the Pi struggles, you can reduce update checks:

```bash
# Edit docker-compose.yml to check every 30 minutes instead of 5
sed -i 's/WATCHTOWER_POLL_INTERVAL=300/WATCHTOWER_POLL_INTERVAL=1800/' docker-compose.yml
docker-compose restart watchtower
```

## Step 6: Monitoring and Maintenance

```bash
# Live logs
docker-compose logs -f smart-home-state

# Last 100 lines
docker-compose logs --tail=100 smart-home-state
```

### Check Application Status

```bash
# Container status
docker-compose ps

# Resource usage
docker stats
```

### Manual Updates (if needed)

```bash
# Pull latest image and restart
docker-compose pull && docker-compose up -d
```

### Backup Configuration

```bash
# Backup your configuration and state
tar -czf backup-$(date +%Y%m%d).tar.gz data/ .env docker-compose.yml
```

## Troubleshooting

### Container Won't Start

1. Check logs: `docker-compose logs smart-home-state`
2. Verify configuration: `cat data/application.conf`
3. Check resource usage: `free -h` and `df -h`

### Watchtower Not Updating

1. Check watchtower logs: `docker-compose logs watchtower`
2. Verify container labels: `docker inspect smart-home-state | grep watchtower`
3. Test manual pull: `docker pull ghcr.io/sgabalda/smart-home-state:latest`

### Network Issues

1. If using host networking, ensure ports aren't conflicting
2. Check MQTT/OpenHAB connectivity from container:
   ```bash
   docker exec smart-home-state netcat -zv mqtt-broker 1883
   ```

## Security Considerations

1. **Firewall:** Only expose necessary ports
2. **Updates:** Keep Raspberry Pi OS updated
3. **Monitoring:** Set up log monitoring for suspicious activity
4. **Backups:** Regular backup of configuration and state data

## Optional: Slack Notifications

To receive notifications when deployments happen:

1. Create a Slack webhook URL
2. Add it to your `.env` file:
   ```
   SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK
   ```
3. Restart watchtower: `docker-compose restart watchtower`
