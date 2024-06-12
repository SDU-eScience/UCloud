<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/accounting/allocations.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/grants/grants.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / Visualization of Usage
# Visualization of Usage
[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Visualization gives the user access to an overview of their usage during a set period or for a given product category. 
It also provides statistics to help understand workflows._

## Usage

Usage is shown in different versions and only for a single product category at a time. When a usage request is made by 
the user usage for all product categories are collected and returned.

### Usage over time chart
Usage over time shows the usage during the selected period for the selected product type

![](/backend/accounting-service/wiki/UsageOverTime.png)

__Figure 1:__ Full usage shown for product type `u1_standard-h` for the period of a week.

### Usage breakdown by sub-projects

Usage breakdown shows the usage during the selected period for the selected product type divided into projects

![](/backend/accounting-service/wiki/UsageBreakdown.png)

__Figure 2:__ Usage shown for product type `u1_standard-h` for the period of a 
week divided into top 4 projects and the rest bundled into _other_.

## Statistics

Statistics offers insight into the subprojects workflows showing data regarding jobs.

### Most used applications
The overview of most used applications shows a list of applications run by the subprojects of the current 
project sorted by frequency.

![](/backend/accounting-service/wiki/MostUsedApplications.png)

__Figure 3:__ Overview of most used applications.

### When are your jobs being submitted?
The overview of when jobs are submitted collects data on when jobs are started and presents it in 6 hour buckets

![](/backend/accounting-service/wiki/Jobs.png)

__Figure 4:__ Overview of when jobs are being submitted.
