package com.journy.backend.weather;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class WeatherRiskEvaluator {
    private final double probabilityThreshold;
    private final double amountThreshold;
    public WeatherRiskEvaluator(@Value("${journy.weather.rain-probability-threshold:60}") double probability,
                                @Value("${journy.weather.rain-amount-threshold-mm:1.0}") double amount) {
        if (!Double.isFinite(probability) || !Double.isFinite(amount) || probability<=0 || probability>100 || amount<=0) throw new IllegalArgumentException("Weather thresholds must be positive and probability <= 100");
        probabilityThreshold=probability; amountThreshold=amount;
    }
    public boolean risky(WeatherForecast.Hour hour) {
        return hour != null && ((hour.precipitationProbability()!=null && hour.precipitationProbability()>=probabilityThreshold)
                || (hour.precipitationAmount()!=null && hour.precipitationAmount()>=amountThreshold));
    }
}
