# Kruize Reference Guide

## What is Kruize?

Kruize is a Kubernetes resource optimization engine that analyzes workload performance and provides intelligent recommendations for right-sizing CPU and memory resources, as well as runtime and framework configurations. It helps reduce cloud costs while maintaining application performance by providing:
- **Container right-sizing recommendations**: CPU and memory requests and limits
- **Runtime recommendations**: JVM settings (GC policies, heap sizes) for Java applications
- **Framework recommendations**: Framework-specific parameters (e.g., Quarkus thread pools)
- **Box plots data**: Detailed usage analysis and visualization

## How Kruize Works

Kruize provides comprehensive optimization recommendations based on resource usage patterns:
- Recommendations are based on resource usage over **24 hours (short term)**, **7 days (medium term)**, and **15 days (long term)**
- Provides both **cost-optimized** and **performance-optimized** suggestions for each term on a per-container basis
- Request and limit values for both CPU and memory are set to be the same for consistency
- Runtime and framework recommendations are included alongside CPU/memory recommendations when prerequisites are met:
    - Application metrics accessible via Prometheus or Thanos
    - Application exposes necessary runtime metrics
    - For Quarkus: Label `com.redhat.component-name: "Quarkus"` added to Deployment or Pod

## Key Concepts

### Experiments

An experiment is a JSON specification that tells Kruize which Kubernetes workloads to monitor and optimize. It includes workload details (namespace, deployment, containers), measurement duration, and recommendation thresholds.

Kruize monitors these workloads through experiments that collect metrics over time, tracking container resource usage (CPU, memory), runtime metrics (JVM, framework-specific), and optimization opportunities.

**Note**: Runtime recommendations are only available for container experiments and require proper metric exposure.

### Recommendation Engines

Kruize provides two types of optimization recommendations:

#### Performance Engine
- **Goal**: Maximize application performance
- **Memory Strategy**: Uses the **max value** in the observed term with an added buffer. The buffer represents the minimum of 20% over the max value and the maximum interval spike in the observed term
- **CPU Strategy**: Uses the **98th percentile** for CPU usage (including any throttling) for the given term
- **Use Case**: Performance-critical workloads where responsiveness matters most
- **Output**: Recommendations that prioritize speed and reliability

#### Cost Engine
- **Goal**: Minimize resource costs
- **Memory Strategy**: Uses the same approach as Performance Engine - **max value** with added buffer (minimum of 20% over max value and maximum interval spike)
- **CPU Strategy**: Uses the **60th percentile** for CPU usage (including throttling) for the given term
- **Use Case**: Cost-sensitive workloads where slight performance trade-offs are acceptable
- **Output**: Recommendations that prioritize savings

**Note**: Kruize also provides capacity and utilization data used to represent resource request versus actual resource utilization data (e.g., as a box plot) to better understand the recommendations. The default interval for metric observation is currently set to 15 minutes.


### Recommendation Terms

Kruize provides recommendations across three time horizons:

- **Short-term**: Based on 24 hours of historical data - Quick wins for immediate adjustments
- **Medium-term**: Based on 7 days of historical data - Balanced approach with better reliability
- **Long-term**: Based on 15 days of historical data - Most reliable recommendations

Longer monitoring periods generally provide more reliable recommendations with better understanding of usage patterns.

### Box Plots Data

Each recommendation includes detailed box plots data that visualizes resource usage patterns:
- **Min/Max Values**: Shows the range of resource usage
- **Median**: Indicates the typical usage level
- **Quartiles**: Helps understand usage distribution
- **Format**: Provides statistical insights into CPU and memory consumption patterns
- **Usage**: Essential for validating recommendation reliability and understanding workload behavior

### Resource Configuration

Kruize recommendations include:
- **Requests**: Minimum guaranteed resources
- **Limits**: Maximum allowed resources
- **Unified Values**: Request and limit values for both CPU and memory are set to be the same
- **Box Plots**: Statistical visualization of usage patterns including min, max, median, and quartiles
- **Runtime Parameters**: JVM and framework configuration recommendations (when applicable)

### Warnings and Notifications

Kruize displays warnings in certain conditions:

1. **Idle Containers (Code 323001)**
    - Containers with < 1 millicore CPU usage
    - No CPU recommendation can be generated
    - Only memory recommendations provided

2. **Missing Request or Limit**
    - Warning when CPU/memory request or limit is not set in current configuration
    - Helps identify incomplete resource configurations

## Runtime and Framework Recommendations

In addition to CPU and memory recommendations, Kruize provides runtime and framework optimization when the prerequisites mentioned above are met.

### Supported Runtime Layers

| Layer Type | Supported Stacks | Primary Tunables |
|------------|------------------|------------------|
| **Runtime** | OpenJDK/Hotspot, IBM Semeru/OpenJ9 | GCPolicy, MaxRAMPercentage |
| **Framework** | Quarkus | quarkus.thread-pool.core-threads |

### How It Works

1. During experiment creation, Kruize automatically detects application layers (e.g., Hotspot, Semeru, Quarkus)
2. Kruize analyzes runtime-specific metrics alongside CPU/memory usage
3. Recommendations include a `runtime_recommendations` section with optimized parameters
4. Apply recommendations as environment variables or configuration changes