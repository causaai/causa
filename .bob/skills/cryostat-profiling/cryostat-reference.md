# Cryostat Reference Guide

## What is Cryostat?

Cryostat is a container-native Java application monitoring and profiling tool that provides JDK Flight Recorder (JFR) management capabilities for containerized Java applications. It enables developers and operators to:
- **Monitor Java applications**: Collect and analyze JFR recordings from containerized Java workloads
- **Profile performance**: Capture detailed runtime metrics and performance data
- **Troubleshoot issues**: Diagnose memory leaks, CPU hotspots, and threading problems
- **Analyze JVM behavior**: Understand garbage collection, thread activity, and resource usage

Cryostat provides a web-based interface and API for managing JFR recordings across multiple Java applications running in containers, making it ideal for cloud-native environments.

## Key Concepts

### JDK Flight Recorder (JFR)

JFR is a profiling and event collection framework built into the JDK:
- **Low overhead**: Designed for production use with minimal performance impact
- **Comprehensive data**: Captures CPU usage, memory allocation, thread activity, I/O operations
- **Event-based**: Records specific events like exceptions, GC pauses, lock contention
- **Time-series data**: Provides historical view of application behavior

### Recording Types

Cryostat supports different recording modes:

#### Continuous Recordings
- **Always-on monitoring**: Runs continuously in the background
- **Circular buffer**: Maintains recent data, older data is overwritten
- **Low overhead**: Minimal impact on application performance
- **Use case**: Ongoing monitoring and post-mortem analysis

#### Snapshot Recordings
- **On-demand capture**: Created when needed for specific analysis
- **Fixed duration**: Runs for a specified time period
- **Detailed data**: Can capture more events than continuous recordings
- **Use case**: Troubleshooting specific issues or performance analysis

#### Archived Recordings
- **Persistent storage**: Saved recordings for later analysis
- **Historical analysis**: Compare performance over time
- **Compliance**: Retain recordings for audit purposes
- **Use case**: Long-term performance tracking and trend analysis

### Event Templates

Templates define which JFR events to record:

#### Continuous Template
- **Minimal overhead**: Records essential events only
- **Always-on**: Suitable for production environments
- **Events**: Basic CPU, memory, thread, and I/O events
- **Use case**: Default monitoring without performance impact

#### Profiling Template
- **Detailed data**: Records comprehensive event set
- **Higher overhead**: More performance impact than continuous
- **Events**: Method profiling, allocation tracking, detailed GC info
- **Use case**: Performance analysis and optimization

#### Custom Templates
- **Tailored monitoring**: Define specific events to record
- **Flexible configuration**: Balance between detail and overhead
- **Event selection**: Choose from 100+ available JFR events
- **Use case**: Specific troubleshooting or analysis needs

### Target Applications

Cryostat discovers and manages Java applications:
- **Automatic discovery**: Finds Java applications via agent registration or JMX
- **Target metadata**: Stores application labels, annotations, and connection info
- **Connection management**: Maintains secure connections to target JVMs
- **Health monitoring**: Tracks target availability and connection status

## Performance Metrics

### CPU Metrics
- **CPU Usage**: Overall CPU consumption by application
- **Thread CPU Time**: CPU time per thread
- **Method Profiling**: Time spent in specific methods
- **Compilation**: JIT compilation activity

### Memory Metrics
- **Heap Usage**: Current heap memory utilization
- **Allocation Rate**: Rate of object allocation
- **GC Activity**: Garbage collection frequency and duration
- **Memory Leaks**: Objects that should be collected but aren't

### Thread Metrics
- **Thread Count**: Number of active threads
- **Thread States**: Running, waiting, blocked threads
- **Lock Contention**: Thread synchronization issues
- **Deadlocks**: Circular thread dependencies

### I/O Metrics
- **File I/O**: Read/write operations and throughput
- **Network I/O**: Socket operations and data transfer
- **I/O Wait Time**: Time spent waiting for I/O operations

## Common Use Cases

### Performance Troubleshooting

**Scenario**: Application experiencing high CPU usage

**Approach**:
1. Start a profiling recording for 60 seconds
2. Analyze method profiling data to identify hot methods
3. Review thread CPU time to find problematic threads
4. Examine compilation events for JIT issues
5. Implement optimizations based on findings

### Memory Leak Detection

**Scenario**: Application memory usage growing over time

**Approach**:
1. Create continuous recording with allocation profiling
2. Monitor heap usage trends over time
3. Analyze allocation patterns to identify leak sources
4. Review GC events to understand collection behavior
5. Use heap dumps for detailed object analysis

### Latency Analysis

**Scenario**: Application experiencing intermittent slowdowns

**Approach**:
1. Enable continuous recording with I/O and lock events
2. Capture recordings during slow periods
3. Analyze thread states and lock contention
4. Review I/O wait times and blocking operations
5. Identify and resolve bottlenecks

### Production Monitoring

**Scenario**: Ongoing monitoring of production applications

**Approach**:
1. Deploy Cryostat Agent with continuous template
2. Configure automatic recording harvesting
3. Set up alerts for anomalous behavior
4. Periodically review archived recordings
5. Maintain historical performance baseline

## Terminology Reference

### Core Terms

- **JFR (JDK Flight Recorder)**: Built-in JVM profiling and diagnostics framework
- **Recording**: A collection of JFR events captured over a time period
- **Event**: A specific occurrence in the JVM (e.g., GC pause, method call, exception)
- **Template**: Configuration defining which events to record and their settings
- **Target**: A Java application that Cryostat can monitor and profile
- **Agent**: Lightweight component running in target JVM for Cryostat integration
- **Archive**: Persistent storage for completed recordings

### Recording States

- **Running**: Recording is actively capturing events
- **Stopped**: Recording has been stopped but not yet archived
- **Archived**: Recording has been saved to persistent storage
- **Deleted**: Recording has been removed from storage

### Event Categories

- **CPU Events**: Method profiling, compilation, thread CPU time
- **Memory Events**: Allocation, GC, heap usage, object statistics
- **Thread Events**: Thread creation, state changes, lock contention
- **I/O Events**: File operations, socket operations, network activity
- **Exception Events**: Thrown exceptions and error conditions
- **JVM Events**: Class loading, JIT compilation, safepoints

### Performance Terms

- **Overhead**: Performance impact of recording on application
- **Sampling**: Periodic checking of application state (lower overhead)
- **Instrumentation**: Code modification to capture events (higher overhead)
- **Threshold**: Minimum duration/size for event to be recorded
- **Buffer**: Memory area where events are stored before writing
- **Harvesting**: Process of collecting and uploading recordings to server

---

**Reference Version**: 1.0  
**Last Updated**: 2026-06-19  
**Based On**: Cryostat 2.x Documentation  
**Maintained By**: causa-backend team