log-synth
=========

The basic idea here is to have a random log generator build fairly realistic files for analysis. This system has two kinds of generators.  The first is intended to generate stuff that looks like web logs.

The second utility provided here is to generate data based on a specified schema.  Skip down to the section on schema-driven generation for information on that.

How to Run the Log Generator
========================

To install and run the web-log generator,

1. Install Java 7, maven and get this software using git.

On a mac, this can help get the right version of Java

    export JAVA_HOME=$(/usr/libexec/java_home)

2. Then do this to build a jar file with all dependencies included

    mvn package

3. Then use this to write one million log lines into the file "log" and to write the associated user database into the file "users".

    java -cp target/log-synth-0.1-SNAPSHOT-jar-with-dependencies.jar com.mapr.synth.Main -count 1M log users

This program will produce a line of output on the standard output for each 10,000 lines of log produced.  Each line will contain the number of log lines produced so far and the number of unique users in the user profile database.


## The Data Source

The data source here is a set of heavily biased random numbers to generate traffic sources, response times and queries. In order to give a realistic long-tail experience the data are generated using special random number generators available in the Mahout library.

There are three basic entities involved in the random process that generates these logs that are IP addresses, users and queries. Users have a basic traffic rate and a variable number of users sit behind each IP address. Queries are composed of words which are generated somewhat differently by each user. The response time for each query is determined based on the terms in the queries with a very few terms causing much longer queries than others. Each log line contains an IP address, a user cookie, a query and a response time.

Logs of various sizes can be generated using the generator tools.

## The Queries

The general goal of the queries is to find out what and/or who is causing long query times and where lots of traffic is coming from.

The questions we would like to answer include:

* What are the top IP addresses by request count?
* What are the top IP addresses by unique user?
* What are the most common search terms?
* What are the most common search terms in the slowest 5% of the queries?
* What is the daily number of searches, (approximate) number of unique users, (approximate) number of unique IP addresses and distribution of response times (average, min, max, 25, 50 and 75%-iles).

## Methods

The general process for generating log lines is to select a user, possibly one we have not seen before. If the user is new, then we need to select an IP address for the user. Otherwise, we remember the IP address for each user.

Queries have an overall frequency distribution that is long-tailed, but each user has a variation on that distribution. In order to model this, we sample each user's queries from a per-user Pittman-Yor process. In order to make users have similar query term distributions, each user's query term distribution is initialized from a Pittman-Yor process that has already been sampled a number of times.

We also need to maintain an average response time per term. The response time for each query is exponentially distributed with a mean equal to the sum of the average response times for the terms. Response times for words are sampled either from an exponential distribution, from a log-gamma distribution or from a gamma distribution with a moderately low shape parameter so that we can have interestingly long tails for response time.

Users are assigned to IP addresses using a Pittman-Yor process with a discount of 0.9. This gives long-tailed distribution to the number of users per IP address. This results in 90% of all IP addresses having only a single user.

Schema-driven Data Generation
=====================

You can also generate data based on a rough schema.  Schema-driven generation supports the generation of addresses, dates, foreign key references, unique id numbers, random integers, sort of realistic personal names and fanciful street names.

In addition to these primitive generators of strings and numbers, nested structures of arrays and objects can also be generated. You can also generate files that link together via ID's so that complex star schema structures can be build.  In a future release, it is anticipated that the generator will execute arbitrary Javascript in order to allow arbitrary dependencies and transformations of data as it is generated.

To generate data, follow the compilation directions above, but use this main program instead:

    java -cp target/log-synth-0.1-SNAPSHOT-jar-with-dependencies.jar com.mapr.synth.Synth -count 1M -schema schema

## Command line arguments

The allowable arguments include:

 `-count n`    Defines how many lines of data to emit.  Default value is 1000.  Suffixes including k, M, and G have customary meanings.

 `-schema file` Defines where to get the schema definition from.  The schema is in JSON format and consists of a list of field specifications.  Each field specification is a JSON object and is required to have the following values

 * `class` - Defines the distribution that is used to sample values for this field.  Possible values include `address`, `date`, `foreign-key`, `id`, `int`, and `street-name`.  Additional values that may be allowed or required for specific generators are detailed below.

 * `name` - This is the name of the field.  The output will consist of fields ordered as in the schema definition and any header file will contain the names for each field as defined by this value.

See the longer examples below.

`-format CSV | TSV | JSON | XML` Defines what format the output should use.  Note that XML format assumes that the root element is called "root" and each record element should be callled "OBJECT_NODE".  There is no way that is what people really want.  Send email with what is really needed.

 `-output output-directory-name`    Designates an output directory. Output files will be created in this directory named according to the pattern `synth-<thread>` where `<thread>` part is replaced by the thread number that created the file.

 `-threads n`  Indicates how many threads to use for generating data.  Requires `-output`.  Note that the schema is
shared across all of the threads so a schema with an id sampler will still generate all consecutive values in order, but the values will be distributed pretty much randomly across the output files.

Note also that the number of threads that gives best throughput is somewhat surprisingly larger than you might think.  >100 threads can be useful.
 
## Samplers Allowed in a Schema

The following classes of values are allowed (in approximately alphabetical order):

**`address`** - This distribution generates fairly plausible, if somewhat fanciful street addresses.  There are no additional parameters allowed.

    {"name":"address", "class":"address"},
    
**`array-flattener`** - This sampler converts a nested list of lists into a flat list.  This can be useful if used in conjunction with the `sequence` sampler (see the example for `sequence`).

**`browser`** - Samples from browser types with kind of plausible frequency distribution.

    {"name":"br", "class":"browser"},

**`country`** - Samples from ISO country codes.

    {"name":"co", "class":"country"},

**`date`** - This distribution generates dates which are some time before an epoch date.  Dates shortly before the epoch are more common than those long before.  On average, the dates generated are 100 days before the epoch.  A format field is allowed which takes a format for the data in the style of Java's SimpleDateFormatter.  Note that the order of options is significant in that the format will apply to the start and end options if it comes before them.  By default, these are formatted like yyyy-MM-dd, but you can specify a different format using the format option.  Dates are selected by default to be before July 1, 2013.  The amount before is selected exponentially with mean of 100 days.  If you specify start or end dates, the dates will be sampled uniformly between your dates. The default start is January 1, 1970.  The default end is July 1, 2013.

    {"name":"first_visit", "class":"date", "format":"MM/dd/yyyy"},
    {"name":"second_date", "class":"date", "start":"2014-01-31", "end":"2014-02-07"},
    {"name":"third_date", "class":"date", "format":"MM/dd/yyyy", "start":"01/31/1995", "end":"02/07/1999"}
    
**`event`** - Samples Poisson distributed event times with specified rates.

    {"name":"foo1", "class":"event", "rate": "0.1/d"},
    {"name":"foo2", "class":"event", "start": "2014-01-01", "format":"yyyy-MM-dd HH:mm:ss", "rate": "10/s"},
    {"name":"foo3", "class":"event", "format": "MM/dd/yyyy HH:mm:ss", "start": "02/01/2014 00:00:00", "rate": "0.5/s"}

**`flatten`** - Turns an object into fields.

Some samplers such as `zip` or `vin` return complex objects with many fields.  If you want to output each of these fields
as a separate field in CSV format, you could post process a JSON output file or you can use the `flatten` sampler
to promote these fields to the level above. As an example, the snippet below results in samples with fields like `zipType`,
`zip`, `latitude`, `longitude` and others.

    {
        "class": "flatten",
        "value": { "class": "zip" },
        "prefix": ""
    }

Notice how there is no name here for the `flatten` sampler.  This is because the resulting values are named using the 
prefix (empty in this example) and the names fo the fields from the sub-sampler (the `zip` sampler in this case).

The prefix used to form the flattened variable names can be specified explicitly, but unless you want it to be empty 
it is usually simpler to just name the `flatten` sampler.  This gives you a default prefix that is the name of the 
flattener with a dash appended.  For instance, this snippet

    {
        "name": "x"
        "class": "flatten",
        "value": { "class": "zip", "fields": "latitude, longitude" },
    },
    {
        "name": "y"
        "class": "flatten",
        "value": { "class": "zip", "fields": "latitude, longitude" },
    }

would give samples with fields named `x-latitude`, `x-longitude`, `y-latitude` and `y-longitude` which makes it
easy to keep track of which fields are associated with each other.

**`foreign-key`** - This distribution generates randomized references to an integer key from another table.  You must specify the size of the table being referenced using the size parameter. The default value of size is 1000.  You may optionally specify a skewness factor in the range [0,3].  A value of 0 gives uniform distribution.  A value of 1 gives a classic Zipf distribution.  The default skew is 0.5.  Values are biased towards smaller values.  This sampler uses space proportional to size so be slightly cautious.

**`id`** - This distribution returns consecutive integers starting at the value of the start parameter.

    {"name":"id", "class":"id"},

**`int`** - Samples values from min (inclusive) to max (exclusive) with an adjustable skew toward small values.  If you set skew to a negative number, larger values will be preferred.

    {"name":"size", "class":"int", "min":10, "max":99}
    {"name": "z", "class": "int", "min": 10, "max": 20, "skew": -1},
    {"name":"x", "class":"lookup", "resource":"data.json", "skew":1},

**`join`** - Glues together an array of strings.  You can specify a separator that goes between the joined strings with the `separator` parameter.  The `value` parameter specifies how to generate the arrays of strings.

This snippet will generate silly file names nested three deep:

    {
      "name": "filename",
      "class": "join",
      "separator": "/",
      "value": {
        "class":"sequence",
        "length":3,
        "array":[
          {"class":"string", "dist":{"top1":10, "top2":5, "top3":2}},
          {"class":"string", "dist":{"mid1":10, "mid2":5, "mid3":2}},
          {"class":"string", "dist":{"alice":10, "bob":5, "charles":2, "dahlia":1, "ephraim":1}}
        ]
      }
    }

**`language`** - Samples from ISO language codes according to prevalence on the web.

    {"name":"la", "class":"language"},
        
**`lookup`** - Samples from lines of a file.

**`map`** - Samples from complex objects, fields of which are sampled according to a recursive schema you specify.

    {
      "name": "stuff",
      "class": "map",
      "value": [
        {"name": "a", "class": "int", "min": 3, "max": 4},
        {"name": "b","class": "int","min": 4,"max": 5}
      ]
    }

**`name`** - Samples from (slightly) plausible names.  The allowable types are
`first`, `last`, `first_last` and `last_first`.  The default type is `first_last`.

    {"name":"name", "class":"name", "type":"first_last"},

**`sequence`** - Repeatedly samples from a single distribution and returns an array of the results.

This example produces variable length results with exponentially distributed lengths.  Some of the results have length 0.

    {"name":"c", "class":"sequence", "base":{"class":"os"}},

This example produces values with lengths that are exponentially distributed with mean length of 10.

    {"name":"d", "class":"sequence", "base":{"class":"int", "min":3, "max":9}, "length":10}

This example produces results that always have three values, each of which has a different distribution.

    {
      "name": "x",
      "class": "sequence",
      "array": [
        {
          "class": "int",
          "min": 3,
          "max": 4
        },
        {
          "class": "int",
          "min": 6,
          "max": 7
        },
        {
          "class": "int",
          "min": 8,
          "max": 9
        }
      ]
    }

**`ssn`** - Samples somewhat realistic SSN's

A social security number (SSN) has fields `ssn`, `state`, `description` and `type`.  The `ssn` field is what you might expect.  The
`state` field is the two letter abbreviation of the state that the SSN was issued in (assuming that the SSN was issued before the 2011 conversion to
random assignment).  The `description` is the longer form of the `state`.  The `type` field can have the value `normal` or `extra`.  The `extra` type applies to locations
that don't correspond to the 52 values that most people might expect (50 states + `DC` + `PR`).

You can limit which fields you get back with default fields of `ssn`, `state``description`.  You can also limit the types of values you get back.

For example:

  {
    "name": "z",
    "class": "ssn"
  },

Or

  {
    "name": "zLimited",
    "class": "ssn",
    "fields": "ssn,state,description,type",
    "types": "normal,extra",
    "seed": 123
  }

As is common with many samplers, you can set the seed if you like.

If you only want a string with the SSN in it, you can set the `verbose` flag to false:

  {
    "name": "z",
    "class": "ssn",
    "verbose": false
  },


**`state`** - Samples from any of the 58 USPS state abbreviations.  Yes, there are 58 possible values.

    {"name":"st", "class":"state"},

**`street-name`** - This distribution generates fanciful three word street names.

**`string`** - This distribution generates a specified distribution of strings.  One parameter called `dist` is 
required.  This parameter should be a structure with string keys and numerical values.  The probability for each 
key is proportional to the value.

    {"name":"foo", "class":"string", "dist":{"YES":0.95, "NO":0.05, "NA":1}}

**`uuid`** - Generates random UUIDs.

**`os`** - Samples from operating system codes.  My own bias will show here.

    {"name":"os", "class":"os"}
    
**`random-walk`** and **`gamma`** - Allows sampling from a random walk.

The `random-walk` sample samples steps from a normal distribution and accumulates those steps into a current position.  
The returned value is the sum of those steps.  

The defaults for the `random-walk` sampler are sensible so that this

    {
        "name": "v1",
        "class": "random-walk",
    }
    
samples steps from a unit normal distribution.  The scale of the steps can be changed by setting the `s` (standard deviation),
`variance` (squared standard deviation) or `precision` (inverse of variance) parameters.  Here is an example of setting the 
scale of the step distribution:

    {
        "name": "v2",
        "class": "random-walk",
        "s": 2,
    },
    
If you are setting the scale to
a constant, the `s` parameter would normally be used.  You can also set these parameters to have values that are themselves
random variables that are sampled each step.  For example, this sets the precision to be sampled from a gamma distribution.
The result of this second-order sampling will be a t-distribution with `dof` = 2.  Using very small values of `dof` gives
a very heavy-tailed distribution that occasionally takes enormous steps.

    {
        "name": "v3",
        "class": "random-walk",
        "precision": {
            "class": "gamma",
            "dof": 2
        }
    }

The `verbose` flag can be set to true.  If `verbose` is not set, or is explicitly set to false, then the value of the
current state will be returned.  If `verbose` is set to true, then the current value and the latest step will both be
returned in a structure.  

Setting the scale of the steps to a random variable is usually done by setting the `precision` parameter to be
sampled from a gamma distribution since the gamma is the conjugate distribution to the normal.  The `gamma` sampler
can be adjusted using `alpha` (shape), `beta` (scale), `rate` (rate or 1/beta) or `dof` and `scale` parameters.  
When used to set the step size distribution for a `random-walk` sampler, it is common to use the `dof` and `scale` 
parameters.  

    {
        "name": "g1",
        "class": "gamma",
        "alpha": 0.2,
        "beta": 0.2
    }

When setting `dof` and `scale`, these are translated as `alpha` = `dof` / 2, `beta` = `scale` * `dof` / 2.

    
**`vin`** - Samples from sort of realistic VIN numbers.

Here is are three different ways to use the VIN sampler.  The first one `v1`, uses a seed to force the generated sequence to be identical every time. 

    {
        "name": "v1",
        "class": "vin",
        "seed": 12,
        "country": "north_america",
        "make": "ford",
        "years": "2007-2011"
    }

The second example uses the verbose setting to generate a JSON structure instead of just a single value containing a VIN.  This structure includes additional clear text information about where the vehicle was supposedly made, what kind of engine and so on.         

    {
        "name": "v2",
        "class": "vin",
        "country": "north_america",
        "make": "ford",
        "years": "2007-2011",
        "verbose": "true"
    }

With `verbose` set to true, the output of the sampler looks like this

    {
        "VIN":"3FAFW33407M000098",
	    "manufacturer":"Ford",
	    "model":"Ford F-Series, F-350, Crew Cab, 4WD, Dual Rear Wheels",
	    "engine":"V6,Essex,3.8 L,EFI,Gasoline,190hp",
	    "year":2007
    }

Note that there can be implausible combinations of engine, year and model such as a 2007 DeLorean.  Also, the sampler currently only has information about Ford and BMW models.  For other makes, the model engine and plant information is just gibberish.

The third example shows how the country and year fields can have more complex constraints.
         
    {
        "name": "v3",
        "class": "vin",
        "countries": "ca, mx",
        "make": "ford",
        "years": "2002,2007-2011"
    }

Currently all sampling for constructing a VIN is done by uniformly sampling all of the possible options.  This could easily be changed if desired.
    

**`word`** - Samples words at random.  A seed file is given, but if more words are needed than seeded, they will be invented.

**`common-point-of-compromise`** - Produces a user history that emulates a common point of compromise fraud scenario.  Contact tdunning@maprtech.com for more info.

    [
        {
            "name": "id",
            "class": "id"
        },
        {
            "name": "history",
            "class": "common-point-of-compromise",
            "seed": 12,
            "exploitStart": "2014-01-20 00:00:00",
            "exploitEnd": "2014-02-20 00:00:00",
            "end": "2014-03-31 00:00:00",
            "compromisedFraudRate": 0.02,
            "uncompromisedFraudRate": 0.001
        }
    ]
    
**`zip`** - Samples from a table of US Zip Codes.  This gives you latitude, longitude and other common parameters for zip codes.  This can be used to generate random ish locations for various purposes that have nothing to do with the postal system.

All parameters for this sampler are optional.

    [
        {
            "name": "z",
            "class": "zip",
            "seed": 12,
            "latitudeFuzz": 1,
            "longitudeFuzz": 1,
            "onlyContinental": "true"
        }
    ]

Zip samplers can also limit the points returned by using a latitude/longitude bounding box or by specifying a single point and a distance radius (in miles).

This gives all zips with centers within 200 miles of a point in Los Angeles

    {
        "name": "zLosAngeles",
        "class": "zip",
        "near": "33.97,-118.24",
        "milesFrom": 200
    }

Note that having a small radius here will make the sampler very slow because it will have to reject many samples.  A radius of 200 miles makes the sampler about 10 times slower.

Likewise, this gives zips that have latitude from 20 to 30 degrees

    {
        "name": "zLosAngeles",
        "class": "zip",
        "latitude": "20,30"
    }

If you only want the zip code as a string without all the supporting information, set the `verbose` flag to false. For example:

    {
        "name": "zLosAngeles",
        "class": "zip",
        "near": "33.97,-118.24",
        "milesFrom": 200,
        "verbose": false
    }


## Longer Examples
The following schema generates a typical fact table from a simulated star schema:

    [
        {"name":"id", "class":"id"},
        {"name":"user_id", "class": "foreign-key", "size": 10000},
        {"name":"item_id", "class": "foreign-key", "size": 2000}
    ]

Here we have an id and two foreign key references to dimension tables for user information and item information.  This definition assumes that we will generate 10,000 users and 2000 item records.

The users can be generated using this schema.

    [
        {"name":"id", "class":"id"},
        {"name":"name", "class":"name", "type":"first_last"},
        {"name":"gender", "class":"string", "dist":{"MALE":0.5, "FEMALE":0.5, "OTHER":0.02}},
        {"name":"address", "class":"address"},
        {"name":"first_visit", "class":"date", "format":"MM/dd/yyyy"}
    ]

For each user we generate an id, a name, an address and a date the user first visited the site.

Items are simpler and are generated using this schema

    [
        {"name":"id", "class":"id"},
        {"name":"size", "class":"int", "min":10, "max":99}
    ]

Each item has an id and a size which is just a random integer from 10 to 99.

You can use the sequence type to generate variable or fixed-length arrays of values which can themselves be complex.  If you use the JSON output format, this structure will be preserved.  If you want to flatten an array produced by sequence, you can use the flatten sampler.

For example, this produces users with names and variable length query strings

    [
        {"name":"user_name", "class":"name", "type": "last_first"},
        {"name":"query", "class":"array-flatten", "value": {
            "class": "sequence", "length":4, "base": {
                "class": "word"
            }
        }}
    ]

If you use the TSV format with this schema, the queries will be comma delimited unquoted strings.  If you omit the `array-flatten` step, you will get a list of strings surrounded by square brackets and each string will be quoted (i.e. an array in JSON format).

You can also generate arbitrarily nested data by using the map sampler.  For example, this schema will produce records with an id and a map named stuff that has two integers ("a" and "b") in it.

    [
        {"name": "id", "class": "id"},
        {
            "name": "stuff", "class": "map",
            "value": [
                {"name": "a", "class": "int", "min": 3, "max": 4},
                {"name": "b", "class": "int", "min": 4, "max": 5}
            ]
        }
    ]


## Quoting of Strings

By default all strings in CSV or TSV formats are fully quoted.  This can confuse some software (sadly) so there are additional options to control how quoting is done.  

There are three basic strategies supported:

* *DOUBLE_QUOTE* This is the previous behavior where all strings are safely quoted according to JSON conventions.

* *BACK_SLASH* With this convention, all internal spaces, tabs, back slashes and commas are quoted by preceding them with backslash character. This convention is useful with Hive.

* *OPTIMISTIC* With this convention, no quoting of strings is done.  This should not normally be used since it is very easy to get unparseable data.

The default convention is DOUBLE_QUOTE.

Template based Data Generation
=====================

This approach uses Freemarker template engine to render custom templates. The data variables in the template are fed from a specified schema.

## Command-line options:

`-template file` link to a Freemarker template

`-schema file` to specify the schema (see above)

## Template notation

To print the value of a variable in the template, use ${name.asText()} placeholder.
