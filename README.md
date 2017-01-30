log-synth
=========

The basic idea here is to have a random data generator build fairly
realistic files for analysis. The primary use of log-synth has been to
generate data based on a specified schema, but there is an older
system to generate data that looks like a particular kind of web
server log. See web-log.md in this directory for more information
about the web log generator.

Schema-driven Data Generation
=====================

Log-synth allows you to generate data based on a rough schema.  Schema-driven generation supports the generation of addresses, dates, foreign key references, unique id numbers, random integers, sort of realistic personal names and fanciful street names.

In addition to these primitive generators of strings and numbers, nested structures of arrays and objects can also be generated. You can also generate files that link together via ID's so that complex star schema structures can be build.  In a future release, it is anticipated that the generator will execute arbitrary Javascript in order to allow arbitrary dependencies and transformations of data as it is generated.

To generate data, follow the compilation directions below, that will
create a standalone executable that you can use to generate data. This
generates a million records using the schema in the file `schema.synth`.

    target/log-synth -count 1M -schema schema.synth

## Command line arguments

The allowable arguments include:

 `-count n`    Defines how many lines of data to emit.  Default value is 1000.  Suffixes including k, M, and G have customary meanings.

 `-schema file` Defines where to get the schema definition from.  The schema is in JSON format and consists of a list of field specifications.  Each field specification is a JSON object and is required to have the following value

 * `class` - Defines the distribution that is used to sample values for this field.  Possible values include `address`, `date`, `foreign-key`, `id`, `int`, and `street-name`.  Additional values that may be allowed or required for specific generators are detailed below.

Commonly, field specifications also need to give a name

 * `name` - This is the name of the field.  The output will consist of fields ordered as in the schema definition and any header file will contain the names for each field as defined by this value.

See the longer examples below for more information.

`-format CSV | TSV | JSON | XML` Defines what format the output should
use.  Note that XML format assumes that the root element is called
"root" and each record element should be callled "OBJECT_NODE".  There
is no way that is what most people really want.  If you need a better
kind of XML format, file an issue or send a pull request with what is
really needed.

Note that the JSON output is a list of individual JSON maps, each one
to a line, to convert such data to a single JSON array, use the following sed command:
``` 
sed -i .bak -e '1 s/^/[\n/' -e '$ s/$/\n]/' -e '$ ! s/$/,/' oldfile.json  
``` 
Note that this only works with gnu sed and won't work with the version
of sed you will find by default on Macs as part of OSX. On OSX, try
this instead
```
sed -i .bak -e '1 s/^/[\'$'\n/' -e '$ s/$/\'$'\n]/' -e '$ ! s/$/,/' oldfile.json 
```
This last form should work on Linux as well, but I haven't tested it.

 `-output output-directory-name`    Designates an output
 directory. Output files will be created in this directory named
 according to the pattern `synth-<thread>.<ext>` where `<thread>` part
 is replaced by the thread number that created the file and `<ext>` is
 replaced by an appropriate file extension.

 `-threads n`  Indicates how many threads to use for generating data.  Requires `-output`.  Note that the schema is
shared across all of the threads so a schema with an id sampler will still generate all consecutive values in order, but the values will be distributed pretty much randomly across the output files.

Note also that the number of threads that gives best throughput is somewhat surprisingly larger than you might think.  >100 threads can be useful.
 
## Samplers Allowed in a Schema

The following classes of values are allowed (in approximately alphabetical order):

#### `address`
This distribution generates fairly plausible, if somewhat fanciful street addresses.  There are no additional parameters allowed.

    {"name":"address", "class":"address"},
        
#### `array-flattener`
This sampler converts a nested list of lists into a flat list.  This can be useful if used in conjunction with the `sequence` sampler (see the example for `sequence`).

#### `browser`
Samples from browser types with kind of plausible frequency distribution.

```json
{"name":"br", "class":"browser"},
```
#### `commuter`
Samples simulated automotive data from commuters.

The idea here is that we have some number of commuters who each have a home and work location. These commuters
tend to drive to work in the morning rush hour and home in the evening rush hour, although they may do either 
commute at other times. While at home, these simulated commuters may decide to run some errands.

Underneath this life-style model is a traffic model that has each driver pick a route either on local roads or
on a highway. Speeds on local roads are lower and more variable than on highways. Highways also go nearly directly to
the destination while local roads are bound to north-sout or east-west directions. The choice of which kind of 
segment to pick depends mostly on the distance to the destination. Note that there is no pre-defined set of roads,
the model just makes it up as segments are chosen. This means that there really isn't any sort of congestion modeling
happening here, just variable speeds.

Below the route planning model is a physical model that involves cars that respond to the control inputs generated
at the higher levels to try to maintain desired speeds. The cars look roughly like they have moderately powerful
engines with 7 speed automatic transmissions. The shift points are set to roughly match a diesel engine. All shifting
is done based on fixed shift points and all throttle settings are done using a simple closed loop model that tries
to match the desired road speed. The performance level of the cars is chosen to be moderately good in that they can
do 0-60 MPH in about 7-8 seconds.

The output from the `commuter` model can be either in nested or flattened form according to whether `flat: true` is 
used in the schema. In nested form there is one record per simulated vehicle. Inside each of these vehicle traces is 
a history of what the vehicle did during the test in the form of a list of trips. Each trip has descriptive information
about the trip such as distance, start time, duration and type (`errand`, `to_home`, `to_work`).

In the flattened form, each sample in the nested form is retained, but all nesting is removed with all of the
fields from the outer structures being repeated in each sample record.

Here is a sample schema for the `commuter` model:

```json
[
    {
        "name": "vehicle",
        "class": "vin",
        "verbose": "true"
    },
    {
        "name": "trip",
        "class": "commuter",
        "home": {
            "class":"zip"  ,
            "fields":"latitude, longitude, zip"
        },
        "work": 20,
        "start": "2015-09-03 0:00:00",
        "end": "2015-09-04 0:00:00"
    }
]
```

Note that the commuter model produce a lot of data per record due to the frequent sampling of engine data. 
This means that you won't get very many output records per second of simulator run-time, especially if 
you ask for long histories. This also means that some tools may choke on the output due to the size of 
each input records. To deal with this, you can produce flattened data, you can generate just a single day 
of data at a time or you can request a feature to make the engine sampling frequency to be extended. Your 
feedback would be helpful here if you need this model.

#### `country`
Samples from ISO country codes.
```json
{"name":"co", "class":"country"},
```
#### `date`
This distribution generates dates which are some time before an epoch date.  Dates shortly before the epoch are more common than those long before.  On average, the dates generated are 100 days before the epoch.  A format field is allowed which takes a format for the data in the style of Java's SimpleDateFormatter.  Note that the order of options is significant in that the format will apply to the start and end options if it comes before them.  By default, these are formatted like yyyy-MM-dd, but you can specify a different format using the format option.  Dates are selected by default to be before July 1, 2013.  The amount before is selected exponentially with mean of 100 days.  If you specify start or end dates, the dates will be sampled uniformly between your dates. The default start is January 1, 1970.  The default end is July 1, 2013.
```json
{"name":"first_visit", "class":"date", "format":"MM/dd/yyyy"},
{"name":"second_date", "class":"date", "start":"2014-01-31", "end":"2014-02-07"},
{"name":"third_date", "class":"date", "format":"MM/dd/yyyy", "start":"01/31/1995", "end":"02/07/1999"}
```    
#### `event`
Samples Poisson distributed event times with specified rates.

```json
{"name":"foo1", "class":"event", "rate": "0.1/d"},
{"name":"foo2", "class":"event", "start": "2014-01-01", "format":"yyyy-MM-dd HH:mm:ss", "rate": "10/s"},
{"name":"foo3", "class":"event", "format": "MM/dd/yyyy HH:mm:ss", "start": "02/01/2014 00:00:00", "rate": "0.5/s"}
```
#### `flatten`
Turns an object into fields.

Some samplers such as `zip` or `vin` return complex objects with many fields.  If you want to output each of these fields
as a separate field in CSV format, you could post process a JSON output file or you can use the `flatten` sampler
to promote these fields to the level above. As an example, the snippet below results in samples with fields like `zipType`,
`zip`, `latitude`, `longitude` and others.

```json
{
   "class": "flatten",
   "value": { "class": "zip" },
   "prefix": ""
}
```
Notice how there is no name here for the `flatten` sampler.  This is because the resulting values are named using the 
prefix (empty in this example) and the names fo the fields from the sub-sampler (the `zip` sampler in this case).

The prefix used to form the flattened variable names can be specified explicitly, but unless you want it to be empty 
it is usually simpler to just name the `flatten` sampler.  This gives you a default prefix that is the name of the 
flattener with a dash appended.  For instance, this snippet

```json
{
   "name": "x",
   "class": "flatten",
   "value": { "class": "zip", "fields": "latitude, longitude" },
},
{
   "name": "y",
   "class": "flatten",
   "value": { "class": "zip", "fields": "latitude, longitude" },
}
```
would give samples with fields named `x-latitude`, `x-longitude`, `y-latitude` and `y-longitude` which makes it
easy to keep track of which fields are associated with each other.

#### `foreign-key`
This distribution generates randomized references to an integer key from another table.  You must specify the size of the table being referenced using the size parameter. The default value of size is 1000.  You may optionally specify a skewness factor in the range [0,3].  A value of 0 gives uniform distribution.  A value of 1 gives a classic Zipf distribution.  The default skew is 0.5.  Values are biased towards smaller values.  This sampler uses space proportional to size so be slightly cautious.

#### `id`
This distribution returns consecutive integers starting at the value of the start parameter.

```json
{"name":"id", "class":"id"},
```
#### `int`
Samples values from min (inclusive) to max (exclusive) with an adjustable skew toward small values.  If you set skew to a negative number, larger values will be preferred.

```json
{"name":"size", "class":"int", "min":10, "max":99}
{"name": "z", "class": "int", "min": 10, "max": 20, "skew": -1},
{"name":"x", "class":"lookup", "resource":"data.json", "skew":1},
```
#### `join`
Glues together an array of strings.  You can specify a separator that goes between the joined strings with the `separator` parameter.  The `value` parameter specifies how to generate the arrays of strings.

This snippet will generate silly file names nested three deep:

```json
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
```
#### `language`
Samples from ISO language codes according to prevalence on the web.

```json
{"name":"la", "class":"language"},
```        
#### `lookup`
Samples from lines of a file.

#### `map`
Samples from complex objects, fields of which are sampled according to a recursive schema you specify.

```json
{
 "name": "stuff",
 "class": "map",
 "value": [
   {"name": "a", "class": "int", "min": 3, "max": 4},
   {"name": "b","class": "int","min": 4,"max": 5}
 ]
}
```
#### `name`
Samples from (slightly) plausible names.  The allowable types are
`first`, `last`, `first_last` and `last_first`.  The default type is `first_last`.

```json
{"name":"name", "class":"name", "type":"first_last"},
```
#### `sequence`
Repeatedly samples from a single distribution and returns an array of the results.

This example produces variable length results with exponentially distributed lengths.  Some of the results have length 0.

```json
{"name":"c", "class":"sequence", "base":{"class":"os"}},
```
This example produces values with lengths that are exponentially distributed with mean length of 10.

```json
{"name":"d", "class":"sequence", "base":{"class":"int", "min":3, "max":9}, "length":10}
```
This example produces results that always have three values, each of which has a different distribution.

```json
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
```
Normally a `sequence` produces arrays of result whose length is randomly chosen from an exponential distribution.  
If you set the `lengthDistribution` parameter instead of the `length` parameter, then you can control how the length is 
chosen. Somewhat confusingly, if you set `lengthDistribution` to a constant you get lists with the same length every 
time.  Here are some examples:
```json
    {
      // generates lists with exactly 5 samples each
      "name": "fixed-length",
      "class":"sequence",
      "lengthDistribution":5
      "base": ...
    }
```
```json
    {
      // generates lists with 5-10 samples in each
      "name": "fixed-length",
      "class":"sequence",
      "lengthDistribution":{"class":"integer", "min":5, "max":10},
      "base": ...
    }
```
#### `ssn`
Samples somewhat realistic SSN's

A social security number (SSN) has fields `ssn`, `state`, `description` and `type`.  The `ssn` field is what you might expect.  The
`state` field is the two letter abbreviation of the state that the SSN was issued in (assuming that the SSN was issued before the 2011 conversion to
random assignment).  The `description` is the longer form of the `state`.  The `type` field can have the value `normal` or `extra`.  The `extra` type applies to locations
that don't correspond to the 52 values that most people might expect (50 states + `DC` + `PR`).

You can limit which fields you get back with default fields of `ssn`, `state``description`.  You can also limit the types of values you get back.

For example:

```json
{
 "name": "z",
 "class": "ssn"
},
```
or

```json
{
 "name": "zLimited",
 "class": "ssn",
 "fields": "ssn,state,description,type",
 "types": "normal,extra",
 "seed": 123
}
```
As is common with many samplers, you can set the seed if you like.

If you only want a string with the SSN in it, you can set the `verbose` flag to false:

```json
{
 "name": "z",
 "class": "ssn",
 "verbose": false
},
```

#### `state`
Samples from any of the 58 USPS state abbreviations.  Yes, there are 58 possible values.

```json
{"name":"st", "class":"state"},
```
#### `street-name`
This distribution generates fanciful three word street names.

#### `string`
This distribution generates a specified distribution of strings.  One parameter called `dist` is 
required.  This parameter should be a structure with string keys and numerical values.  The probability for each 
key is proportional to the value.

```json
{"name":"foo", "class":"string", "dist":{"YES":0.95, "NO":0.05, "NA":1}}
```
#### `uuid`
Generates random UUIDs.

#### `os`
Samples from operating system codes.  My own bias will show here.

```json
{"name":"os", "class":"os"}
```    
#### `random-walk` and `gamma`
Allows sampling from a random walk.

The `random-walk` sample samples steps from a normal distribution and accumulates those steps into a current position.  
The returned value is the sum of those steps.  

The defaults for the `random-walk` sampler are sensible so that this

```json
{
   "name": "v1",
   "class": "random-walk",
}
```    
samples steps from a unit normal distribution.  The scale of the steps can be changed by setting the `s` (standard deviation),
`variance` (squared standard deviation) or `precision` (inverse of variance) parameters.  Here is an example of setting the 
scale of the step distribution:

```json
{
   "name": "v2",
   "class": "random-walk",
   "s": 2,
},
```    
If you are setting the scale to
a constant, the `s` parameter would normally be used.  You can also set these parameters to have values that are themselves
random variables that are sampled each step.  For example, this sets the precision to be sampled from a gamma distribution.
The result of this second-order sampling will be a t-distribution with `dof` = 2.  Using very small values of `dof` gives
a very heavy-tailed distribution that occasionally takes enormous steps.

```json
{
   "name": "v3",
   "class": "random-walk",
   "precision": {
       "class": "gamma",
       "dof": 2
   }
}
```
The `verbose` flag can be set to true.  If `verbose` is not set, or is explicitly set to false, then the value of the
current state will be returned.  If `verbose` is set to true, then the current value and the latest step will both be
returned in a structure.  

Setting the scale of the steps to a random variable is usually done by setting the `precision` parameter to be
sampled from a gamma distribution since the gamma is the conjugate distribution to the normal.  The `gamma` sampler
can be adjusted using `alpha` (shape), `beta` (scale), `rate` (rate or 1/beta) or `dof` and `scale` parameters.  
When used to set the step size distribution for a `random-walk` sampler, it is common to use the `dof` and `scale` 
parameters.  

```json
{
   "name": "g1",
   "class": "gamma",
   "alpha": 0.2,
   "beta": 0.2
}
```
When setting `dof` and `scale`, these are translated as `alpha` = `dof` / 2, `beta` = `scale` * `dof` / 2.

If desired, the mean step size can also be set either to a constant or a distribution.  This helps model walks that
have a consistent drift.  As an example, we could model the sampling times for a data acquisition that makes a
measurement every 100 microseconds with 2 microseconds of jitter this way:

```json
{
   "name": "t",
   "class": "random-walk",
   "mean":100,
   "sd":5
}
```
    
#### `vin`
Samples from sort of realistic VIN numbers.

Here is are three different ways to use the VIN sampler.  The first one `v1`, uses a seed to force the generated sequence to be identical every time. 

```json
{
   "name": "v1",
   "class": "vin",
   "seed": 12,
   "country": "north_america",
   "make": "ford",
   "years": "2007-2011"
}
```
The second example uses the verbose setting to generate a JSON structure instead of just a single value containing a VIN.  This structure includes additional clear text information about where the vehicle was supposedly made, what kind of engine and so on.         

```json
{
   "name": "v2",
   "class": "vin",
   "country": "north_america",
   "make": "ford",
   "years": "2007-2011",
   "verbose": "true"
}
```
With `verbose` set to true, the output of the sampler looks like this

```json
{
   "VIN":"3FAFW33407M000098",
	    "manufacturer":"Ford",
	    "model":"Ford F-Series, F-350, Crew Cab, 4WD, Dual Rear Wheels",
	    "engine":"V6,Essex,3.8 L,EFI,Gasoline,190hp",
	    "year":2007
}
```
Note that there can be implausible combinations of engine, year and model such as a 2007 DeLorean.  Also, the sampler currently only has information about Ford and BMW models.  For other makes, the model engine and plant information is just gibberish.

The third example shows how the country and year fields can have more complex constraints.
         
```json
{
   "name": "v3",
   "class": "vin",
   "countries": "ca, mx",
   "make": "ford",
   "years": "2002,2007-2011"
}
```
Currently all sampling for constructing a VIN is done by uniformly sampling all of the possible options.  This could easily be changed if desired.
    

#### `word`
Samples words at random.  A seed file is given, but if more words are needed than seeded, they will be invented.

#### `common-point-of-compromise`
Produces a user history that emulates a common point of compromise fraud scenario.  Contact tdunning@maprtech.com for more info.

```json
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
```    
#### `zip`
Samples from a table of US Zip Codes.  This gives you latitude, longitude and other common parameters for zip codes.  This can be used to generate random ish locations for various purposes that have nothing to do with the postal system.

All parameters for this sampler are optional.

```json
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
```
Zip samplers can also limit the points returned by using a latitude/longitude bounding box or by specifying a single point and a distance radius (in miles).

This gives all zips with centers within 200 miles of a point in Los Angeles

```json
{
   "name": "zLosAngeles",
   "class": "zip",
   "near": "33.97,-118.24",
   "milesFrom": 200
}
```
Note that having a small radius here will make the sampler very slow because it will have to reject many samples.  A radius of 200 miles makes the sampler about 10 times slower.

Likewise, this gives zips that have latitude from 20 to 30 degrees

```json
{
   "name": "zLosAngeles",
   "class": "zip",
   "latitude": "20,30"
}
```
If you only want the zip code as a string without all the supporting information, set the `verbose` flag to false. For example:

```json
{
   "name": "zLosAngeles",
   "class": "zip",
   "near": "33.97,-118.24",
   "milesFrom": 200,
   "verbose": false
}
```

## Longer Examples
The following schema generates a typical fact table from a simulated star schema:

```json
[
   {"name":"id", "class":"id"},
   {"name":"user_id", "class": "foreign-key", "size": 10000},
   {"name":"item_id", "class": "foreign-key", "size": 2000}
]
```
Here we have an id and two foreign key references to dimension tables for user information and item information.  This definition assumes that we will generate 10,000 users and 2000 item records.

The users can be generated using this schema.

```json
[
   {"name":"id", "class":"id"},
   {"name":"name", "class":"name", "type":"first_last"},
   {"name":"gender", "class":"string", "dist":{"MALE":0.5, "FEMALE":0.5, "OTHER":0.02}},
   {"name":"address", "class":"address"},
   {"name":"first_visit", "class":"date", "format":"MM/dd/yyyy"}
]
```
For each user we generate an id, a name, an address and a date the user first visited the site.

Items are simpler and are generated using this schema

```json
[
   {"name":"id", "class":"id"},
   {"name":"size", "class":"int", "min":10, "max":99}
]
```
Each item has an id and a size which is just a random integer from 10 to 99.

You can use the sequence type to generate variable or fixed-length arrays of values which can themselves be complex.  If you use the JSON output format, this structure will be preserved.  If you want to flatten an array produced by sequence, you can use the flatten sampler.

For example, this produces users with names and variable length query strings

```json
[
   {"name":"user_name", "class":"name", "type": "last_first"},
   {"name":"query", "class":"array-flatten", "value": {
       "class": "sequence", "length":4, "base": {
           "class": "word"
       }
   }}
]
```
If you use the TSV format with this schema, the queries will be comma delimited unquoted strings.  If you omit the `array-flatten` step, you will get a list of strings surrounded by square brackets and each string will be quoted (i.e. an array in JSON format).

You can also generate arbitrarily nested data by using the map sampler.  For example, this schema will produce records with an id and a map named stuff that has two integers ("a" and "b") in it.

```json
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
```

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

To generate a sample vCard simply create a file template.txt:
```
BEGIN:VCARD
VERSION:3.0
N:${last_name.asText()};${first_name.asText()};;${title.asText()}
ORG:Sample Org
TITLE:${title.asText()}
PHOTO;VALUE=URL;TYPE=GIF:http://thumbs.example.com/${filename.asText()}/${first_name.asText()?lower_case  }.gif
TEL;TYPE=HOME,VOICE:${phone_number.asText()}
ADR;TYPE=WORK:;;${address.asText()?split(" ")?join(";")}
EMAIL;TYPE=PREF,INTERNET:${first_name.asText()[0]?lower_case}${last_name.asText()?lower_case}@example.com
REV:${first_visit.asText()}
END:VCARD
```
Then a schema file, let’s call it schema.txt:
```jsonjson
[
    {"name":"title", "class":"string", "dist":{"Mr":0.5, "Mrs.":0.14, "Miss":0.36}},
    {"name":"first_name", "class":"name", "type":"first"},
    {"name":"last_name", "class":"name", "type":"last"},

    {"name": "filename", "class": "join", "separator": "/", "value": {
          "class":"sequence",
          "length":2,
          "array":[
              {"class":"string", "dist":{"small":10, "medium":5, "large":2}},
              {"class":"string", "dist":{"high":10, "low":5, "mobile":15}}
          ]
     }},
    {"name": "phone_number", "class": "join", "separator": "-", "value": {
          "class":"sequence",
          "length":3,
          "array":[
              { "class": "int", "min": 100, "max": 999},
              { "class": "int", "min": 100, "max": 999},
              { "class": "int", "min": 100, "max": 999}
          ]
    }},

    {"name":"address", "class":"address"},
    {"name":"first_visit", "class":"date", "format":"yyyy-MM-dd HH:mm:ssZ"}
]
```
To invoke the log-synth, just do:

./target/log-synth -count 5000 -schema schema.txt -template template.txt -format TEMPLATE -output output/

The output documents will end up in the output/ folder as expected and they will look like:
```
BEGIN:VCARD
VERSION:3.0
N:Kittle;Gwendolyn;;Mr
ORG:Sample Org
TITLE:Mr
PHOTO;VALUE=URL;TYPE=GIF:http://thumbs.example.com/small/mobile/gwendolyn.gif
TEL;TYPE=HOME,VOICE:774-383-580
ADR;TYPE=WORK:;;18033;Quaking;Brook;Avenue
EMAIL;TYPE=PREF,INTERNET:gkittle@example.com
REV:2013-07-14 01:37:08+0100
END:VCARDBEGIN:VCARD
```

