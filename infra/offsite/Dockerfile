FROM python:3.13-slim-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends age rclone ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY backup.py /app/backup.py
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python3", "/app/backup.py"]
CMD ["loop"]
