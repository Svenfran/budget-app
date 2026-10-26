import { HttpClient } from "@angular/common/http";
import { Injectable, signal } from "@angular/core";
import { timeout } from "rxjs";
import { environment } from "src/environments/environment";
import { INIT_NUMBERS, INIT_VALUES } from "../constants/default-values";

@Injectable({
  providedIn: 'root'
})
export class HealthCheckService {
    
    public backendStatus = signal<string>(INIT_VALUES.HEALTH_DOWN);
    private apiBaseUrl = environment.apiBaseUrl;
    private healthUrl = `${this.apiBaseUrl}/actuator/health`;

    constructor(
        private http: HttpClient
    ) {}

    getHealthStatus(onComplete?: () => void): void {
        this.http
            .get<any>(this.healthUrl)
            // ohne Antwort (Server/Netz hängt) nicht auf den Browser-Timeout warten
            .pipe(timeout(INIT_NUMBERS.HEALTH_CHECK_TIMEOUT_MS))
            .subscribe({
                next: (result) => {
                    this.backendStatus.set(result.status);
                    onComplete?.();
                },
                error: (err) => {
                    console.error('Error fetching health status:', err);
                    this.backendStatus.set(INIT_VALUES.HEALTH_DOWN);
                    onComplete?.();
                }
            })
    }
}