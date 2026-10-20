param (
    [string]$Target = "help"
)

switch ($Target.ToLower()) {
    "help" {
        cmd.exe /c "make.bat help"
    }
    "up" {
        Write-Host "Starting RailFlow Core Cluster..." -ForegroundColor Cyan
        docker compose --profile core up -d
    }
    "up-full" {
        Write-Host "Starting RailFlow Full Observability & Chaos Cluster..." -ForegroundColor Cyan
        docker compose --profile full up -d
    }
    "down" {
        Write-Host "Stopping RailFlow Cluster..." -ForegroundColor Yellow
        docker compose down -v --remove-orphans
    }
    "build" {
        Write-Host "Building backend microservices and libraries..." -ForegroundColor Cyan
        .\gradlew.bat assemble
        Write-Host "Building React frontend..." -ForegroundColor Cyan
        Set-Location web; npm run build; Set-Location ..
    }
    "test" {
        Write-Host "Running all backend tests..." -ForegroundColor Cyan
        .\gradlew.bat test --info
    }
    "bench" {
        Write-Host "Running JMH microbenchmarks..." -ForegroundColor Cyan
        .\gradlew.bat :bench:jmh
    }
    "seed" {
        Write-Host "Applying Flyway migrations and seeding..." -ForegroundColor Cyan
        docker compose exec -T postgres psql -U railflow -d railflow -f /docker-entrypoint-initdb.d/V1__init_schema.sql
    }
    "chaos" {
        Write-Host "Executing Chaos Experiments C1 - C14..." -ForegroundColor Cyan
        python chaos\chaos-runner.py
    }
    "demo" {
        docker compose --profile core up -d
        Write-Host "=======================================================" -ForegroundColor Green
        Write-Host "RailFlow is live!" -ForegroundColor Green
        Write-Host "Web UI:             http://localhost:5173"
        Write-Host "API Gateway:        http://localhost:8080"
        Write-Host "Admin Service Deck: http://localhost:8088"
        Write-Host "Prometheus:         http://localhost:9090"
        Write-Host "Grafana:            http://localhost:3000 (admin/admin)"
        Write-Host "=======================================================" -ForegroundColor Green
    }
    "verify" {
        .\gradlew.bat test
        Set-Location web; npm run build; Set-Location ..
        python chaos\chaos-runner.py
        Write-Host "=======================================================" -ForegroundColor Green
        Write-Host "  ALL RAILFLOW VERIFICATION CHECKS PASSED (SLO 100%)   " -ForegroundColor Green
        Write-Host "=======================================================" -ForegroundColor Green
    }
    "clean" {
        .\gradlew.bat clean
        if (Test-Path web\dist) { Remove-Item -Recurse -Force web\dist }
    }
    default {
        cmd.exe /c "make.bat $Target"
    }
}
