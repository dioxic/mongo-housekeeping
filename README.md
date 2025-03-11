# Mongo Housekeeping

This tool deletes data based on configurable criteria and at a configurable rate.

All **configuration**, **logging** and **status** are contained in MongoDB in the housekeeping database.

Any changes to the configuration will be automatically picked up and applied - it is not required to restart the application.

## Features

* configurable criteria for deletion
* configurable deletion rate
* database load thresholds to vary the deletion rate
* housekeeping window to only perform deletion during set time periods

## Build

The following command will build, test and package the tool:

```shell
./gradlew installdist
```

The packaged code can be found in the `build/distributions` directory in both `tar` and `zip` formats.

## Execution

Run either the linux or windows script in the package to execute the tool.

e.g. `bin/mongo-housekeeping`

### Usage

```
Usage: app [<options>]

Options:
  --mongo-uri=<text>  MongoDB connection string (default: mongodb://localhost:27017)
  --db=<text>         The housekeeping database (default: housekeeping)
  -h, --help          Show this message and exit
```

If no arguments are provided, the default mongodb connection of `mongodb://localhost:27017` will be used.

## Configuration

Configuration data is stored in the `config` collection. If there is no configuration document present on startup, an example config will be created.

The configuration document contains the following fields:

| Field    | Description                                                           |
|----------|-----------------------------------------------------------------------|
| enabled  | A boolean flag that enables/disables housekeeping                     |
| criteria | A subdocument that contains the critiera for deletion                 |
| rates    | An array of deletion rates based on database metrics                  |
| windows  | An array of time windows that housekeeping will run within (optional) |

### Criteria

There are two types of deletion criteria: **simple** and **pipeline**.
Multiple criteria can be provided for each type and both types can be included in the same config. 

The **simple** criteria type deletes data from a single collection
and expects a query compatible with a mongodb [find](https://www.mongodb.com/docs/manual/reference/method/db.collection.find/#std-label-method-find-query) operation.

The example below will delete data from the `myDatabase.myCollection` namespace.

```json
{
    "namespace": "myDatabase.myCollection",
    "query": {
      "field": {
        "$gt": 9
      },
      "status": "CLOSED"
    }
}
```

The **pipeline** criteria type can delete data across multiple collections and
expects a query in the mongodb [aggregation](https://www.mongodb.com/docs/manual/reference/method/db.collection.aggregate/) format.

The output of the pipeline must conform to a particular format where the field name
denotes the collection and the value is the primary key of the documents to be deleted.

```json
{
  "collection1": 34,
  "collection2": ["abc", "xyz"]
}
```

All documents referred to in the same output document will be deleted _transactionally_.

The example below will delete documents from collection `a`
where `field` is greater than 5 and all related documents from collection `b`.

```json
{
    "db": "myDatabase",
    "rootCollection": "a",
    "query": [
      {
        "$match": {
          "field": {
            "$gt": 5
          }
        }
      },
      {
        "$lookup": {
          "from": "b",
          "localField": "_id",
          "foreignField": "fk",
          "as": "b"
        }
      },
      {
        "$project": {
          "a": "$_id",
          "b": "$b._id"
        }
      }
    ]
}
```

### Rates

**Rates** determine how fast the data will be deleted (i.e. throttling).
For **simple** criteria, the rate equates to the number of documents deleted per second.
For **pipeline** criteria, the rate is the number of transactions per second (which may contain more than one document).

Each rate has a metric threshold that determines when this rate will be active.
There are several different metrics which can be used:

| Metric  | Description                                 |
|---------|---------------------------------------------|
| insert  | The number of insert operations per second  |
| query   | The number of query operations per second   |
| update  | The number of update operations per second  |
| delete  | The number of delete operations per second  |
| getmore | The number of getmore operations per second |
| command | The number of command operations per second |

In the case that multiple rates are within the specified threshold, the lowest rate will be used.
If no rates are within threshold then housekeeping is disabled.

The example below will activate a rate of 5 when there are between 0 and 100 insert operations per second occurring on the database.

```json
{
  "rate": 5,
  "criteria": [
    {
      "metric": "insert",
      "min": 0,
      "max": 100
    }
  ]
}
```

### Windows

Windows determine when the housekeeping activity can be active. Multiple windows can be defined.

The example below means housekeeping can only be active between the hours of 00:00 and 14:00 on Tuesday and Sunday.

```json
{
  "from": "00:00",
  "to": "14:00",
  "days": [
    "TUESDAY",
    "SUNDAY"
  ]
}
```

## State

The `state` collection in the housekeeping database shows the current state of the housekeeping.

```json
{
  "_id": "STATE",
  "enabled": true,
  "window": "open",
  "rate": 20,
  "status": "Processing complete",
  "dbMetrics": {
    "insert": 0,
    "query": 0,
    "update": 0,
    "delete": 0,
    "getmore": 0,
    "command": 0
  },
  "lastUpdated": {
    "$date": "2025-03-11T16:15:54.058Z"
  }
}
```

## Logging

The `log` collection in the housekeeping database contains the log entries from the housekeeping tool.