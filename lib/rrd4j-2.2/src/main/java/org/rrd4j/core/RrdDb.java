package org.rrd4j.core;

import org.rrd4j.ConsolFun;

import java.io.*;
import java.util.Date;

/**
 * <p>Main class used to create and manipulate round robin databases (RRDs). Use this class to perform
 * update and fetch operations on existing RRDs, to create new RRD from
 * the definition (object of class {@link org.rrd4j.core.RrdDef RrdDef}) or
 * from XML file (dumped content of RRDTool's or Rrd4j's RRD file).</p>
 * <p/>
 * <p>Each RRD is backed with some kind of storage. For example, RRDTool supports only one kind of
 * storage (disk file). On the contrary, Rrd4j gives you freedom to use other storage (backend) types
 * even to create your own backend types for some special purposes. Rrd4j by default stores
 * RRD data in files (as RRDTool), but you might choose to store RRD data in memory (this is
 * supported in Rrd4j), to use java.nio.* instead of java.io.* package for file manipulation
 * (also supported) or to store whole RRDs in the SQL database
 * (you'll have to extend some classes to do this).</p>
 * <p/>
 * <p>Note that Rrd4j uses binary format different from RRDTool's format. You cannot
 * use this class to manipulate RRD files created with RRDTool. <b>However, if you perform
 * the same sequence of create, update and fetch operations, you will get exactly the same
 * results from Rrd4j and RRDTool.</b><p>
 * <p/>
 * <p/>
 * You will not be able to use Rrd4j API if you are not familiar with
 * basic RRDTool concepts. Good place to start is the
 * <a href="http://people.ee.ethz.ch/~oetiker/webtools/rrdtool/tutorial/rrdtutorial.html">official RRD tutorial</a>
 * and relevant RRDTool man pages: <a href="../../../../man/rrdcreate.html" target="man">rrdcreate</a>,
 * <a href="../../../../man/rrdupdate.html" target="man">rrdupdate</a>,
 * <a href="../../../../man/rrdfetch.html" target="man">rrdfetch</a> and
 * <a href="../../../../man/rrdgraph.html" target="man">rrdgraph</a>.
 * For RRDTool's advanced graphing capabilities (RPN extensions), also supported in Rrd4j,
 * there is an excellent
 * <a href="http://oss.oetiker.ch/rrdtool/tut/cdeftutorial.en.html" target="man">CDEF tutorial</a>.
 * </p>
 *
 * @see RrdBackend
 * @see RrdBackendFactory
 */
public class RrdDb implements RrdUpdater {
    /**
     * Prefix to identify external XML file source used in various RrdDb constructors.
     */
    public static final String PREFIX_XML = "xml:/";

    /**
     * Prefix to identify external RRDTool file source used in various RrdDb constructors.
     */
    public static final String PREFIX_RRDTool = "rrdtool:/";

    // static final String RRDTOOL = "rrdtool";
    static final int XML_BUFFER_CAPACITY = 100000; // bytes

    private RrdBackend backend;
    private RrdAllocator allocator = new RrdAllocator();

    private final Header header;
    private final Datasource[] datasources;
    private final Archive[] archives;

    private boolean closed = false;

    /**
     * <p>Constructor used to create new RRD object from the definition. This RRD object will be backed
     * with a storage (backend) of the default type. Initially, storage type defaults to "NIO"
     * (RRD bytes will be put in a file on the disk). Default storage type can be changed with a static
     * {@link RrdBackendFactory#setDefaultFactory(String)} method call.</p>
     * <p/>
     * <p>New RRD file structure is specified with an object of class
     * {@link RrdDef <b>RrdDef</b>}. The underlying RRD storage is created as soon
     * as the constructor returns.</p>
     * <p/>
     * <p>Typical scenario:</p>
     * <p/>
     * <pre>
     * // create new RRD definition
     * RrdDef def = new RrdDef("test.rrd", 300);
     * def.addDatasource("input", DsType.DT_COUNTER, 600, 0, Double.NaN);
     * def.addDatasource("output", DsType.DT_COUNTER, 600, 0, Double.NaN);
     * def.addArchive(ConsolFun.CF_AVERAGE, 0.5, 1, 600);
     * def.addArchive(ConsolFun.CF_AVERAGE, 0.5, 6, 700);
     * def.addArchive(ConsolFun.CF_AVERAGE, 0.5, 24, 797);
     * def.addArchive(ConsolFun.CF_AVERAGE, 0.5, 288, 775);
     * def.addArchive(ConsolFun.CF_MAX, 0.5, 1, 600);
     * def.addArchive(ConsolFun.CF_MAX, 0.5, 6, 700);
     * def.addArchive(ConsolFun.CF_MAX, 0.5, 24, 797);
     * def.addArchive(ConsolFun.CF_MAX, 0.5, 288, 775);
     * <p/>
     * // RRD definition is now completed, create the database!
     * RrdDb rrd = new RrdDb(def);
     * // new RRD file has been created on your disk
     * </pre>
     *
     * @param rrdDef Object describing the structure of the new RRD file.
     * @throws IOException Thrown in case of I/O error.
     */
    public RrdDb(RrdDef rrdDef) throws IOException {
        this(rrdDef, RrdBackendFactory.getDefaultFactory());
    }

    /**
     * Constructor used to create new RRD object from the definition object but with a storage
     * (backend) different from default.
     *
     * <p>Rrd4j uses <i>factories</i> to create RRD backend objects. There are three different
     * backend factories supplied with Rrd4j, and each factory has its unique name:</p>
     * <p/>
     * <ul>
     * <li><b>FILE</b>: backends created from this factory will store RRD data to files by using
     * java.io.* classes and methods
     * <li><b>NIO</b>: backends created from this factory will store RRD data to files by using
     * java.nio.* classes and methods
     * <li><b>MEMORY</b>: backends created from this factory will store RRD data in memory. This might
     * be useful in runtime environments which prohibit disk utilization, or for storing temporary,
     * non-critical data (it gets lost as soon as JVM exits).
     * </ul>
     * <p/>
     * <p>For example, to create RRD in memory, use the following code</p>
     * <pre>
     * RrdBackendFactory factory = RrdBackendFactory.getFactory("MEMORY");
     * RrdDb rrdDb = new RrdDb(rrdDef, factory);
     * rrdDb.close();
     * </pre>
     * <p/>
     * <p>New RRD file structure is specified with an object of class
     * {@link RrdDef <b>RrdDef</b>}. The underlying RRD storage is created as soon
     * as the constructor returns.</p>
     *
     * @param rrdDef  RRD definition object
     * @param factory The factory which will be used to create storage for this RRD
     * @throws IOException Thrown in case of I/O error
     * @see RrdBackendFactory
     */
    public RrdDb(RrdDef rrdDef, RrdBackendFactory factory) throws IOException {
        if (!rrdDef.hasDatasources()) {
            throw new IllegalArgumentException("No RRD datasource specified. At least one is needed.");
        }
        if (!rrdDef.hasArchives()) {
            throw new IllegalArgumentException("No RRD archive specified. At least one is needed.");
        }

        String path = rrdDef.getPath();
        backend = factory.open(path, false);
        try {
            backend.setLength(rrdDef.getEstimatedSize());
            // create header
            header = new Header(this, rrdDef);
            // create datasources
            DsDef[] dsDefs = rrdDef.getDsDefs();
            datasources = new Datasource[dsDefs.length];
            for (int i = 0; i < dsDefs.length; i++) {
                datasources[i] = new Datasource(this, dsDefs[i]);
            }
            // create archives
            ArcDef[] arcDefs = rrdDef.getArcDefs();
            archives = new Archive[arcDefs.length];
            for (int i = 0; i < arcDefs.length; i++) {
                archives[i] = new Archive(this, arcDefs[i]);
            }
        }
        catch (IOException e) {
            backend.close();
            throw e;
        }
    }

    /**
     * Constructor used to open already existing RRD. This RRD object will be backed
     * with a storage (backend) of the default type (file on the disk). Constructor
     * obtains read or read/write access to this RRD.
     *
     * @param path     Path to existing RRD.
     * @param readOnly Should be set to <code>false</code> if you want to update
     *                 the underlying RRD. If you want just to fetch data from the RRD file
     *                 (read-only access), specify <code>true</code>. If you try to update RRD file
     *                 open in read-only mode (<code>readOnly</code> set to <code>true</code>),
     *                 <code>IOException</code> will be thrown.
     * @throws IOException Thrown in case of I/O error.
     */
    public RrdDb(String path, boolean readOnly) throws IOException {
        this(path, readOnly, RrdBackendFactory.getDefaultFactory());
    }

    /**
     * Constructor used to open already existing RRD backed
     * with a storage (backend) different from default. Constructor
     * obtains read or read/write access to this RRD.
     *
     * @param path     Path to existing RRD.
     * @param readOnly Should be set to <code>false</code> if you want to update
     *                 the underlying RRD. If you want just to fetch data from the RRD file
     *                 (read-only access), specify <code>true</code>. If you try to update RRD file
     *                 open in read-only mode (<code>readOnly</code> set to <code>true</code>),
     *                 <code>IOException</code> will be thrown.
     * @param factory  Backend factory which will be used for this RRD.
     * @throws FileNotFoundException Thrown if the requested file does not exist.
     * @throws IOException           Thrown in case of general I/O error (bad RRD file, for example).
     * @see RrdBackendFactory
     */
    public RrdDb(String path, boolean readOnly, RrdBackendFactory factory) throws IOException {
        // opens existing RRD file - throw exception if the file does not exist...
        if (!factory.exists(path)) {
            throw new FileNotFoundException("Could not open " + path + " [non existent]");
        }
        backend = factory.open(path, readOnly);
        try {
            // restore header
            header = new Header(this, (RrdDef) null);

            if (factory.shouldValidateHeader(path)) {
                header.validateHeader();
            }

            // restore datasources
            int dsCount = header.getDsCount();
            datasources = new Datasource[dsCount];
            for (int i = 0; i < dsCount; i++) {
                datasources[i] = new Datasource(this, null);
            }
            // restore archives
            int arcCount = header.getArcCount();
            archives = new Archive[arcCount];
            for (int i = 0; i < arcCount; i++) {
                archives[i] = new Archive(this, null);
            }
        }
        catch (IOException e) {
            backend.close();
            throw e;
        }
    }

    /**
     * <p>Constructor used to open already existing RRD in R/W mode, with a default storage
     * (backend) type (file on the disk).
     *
     * @param path Path to existing RRD.
     * @throws IOException Thrown in case of I/O error.
     */
    public RrdDb(String path) throws IOException {
        this(path, false);
    }

    /**
     * <p>Constructor used to open already existing RRD in R/W mode with a storage (backend) type
     * different from default.</p>
     *
     * @param path    Path to existing RRD.
     * @param factory Backend factory used to create this RRD.
     * @throws IOException Thrown in case of I/O error.
     * @see RrdBackendFactory
     */
    public RrdDb(String path, RrdBackendFactory factory) throws IOException {
        this(path, false, factory);
    }

    /**
     * <p>Constructor used to create RRD files from external file sources.
     * Supported external file sources are:</p>
     * <p/>
     * <ul>
     * <li>RRDTool/Rrd4j XML file dumps (i.e files created with <code>rrdtool dump</code> command).
     * <li>RRDTool binary files.
     * </ul>
     * <p/>
     * <p>Newly created RRD will be backed with a default storage (backend) type
     * (file on the disk).</p>
     * <p/>
     * <p>Rrd4j and RRDTool use the same format for XML dump and this constructor should be used to
     * (re)create Rrd4j RRD files from XML dumps. First, dump the content of a RRDTool
     * RRD file (use command line):</p>
     * <p/>
     * <pre>
     * rrdtool dump original.rrd > original.xml
     * </pre>
     * <p/>
     * <p>Than, use the file <code>original.xml</code> to create Rrd4j RRD file named
     * <code>copy.rrd</code>:</p>
     * <p/>
     * <pre>
     * RrdDb rrd = new RrdDb("copy.rrd", "original.xml");
     * </pre>
     * <p/>
     * <p>or:</p>
     * <p/>
     * <pre>
     * RrdDb rrd = new RrdDb("copy.rrd", "xml:/original.xml");
     * </pre>
     * <p/>
     * <p>See documentation for {@link #dumpXml(String) dumpXml()} method
     * to see how to convert Rrd4j files to RRDTool's format.</p>
     * <p/>
     * <p>To read RRDTool files directly, specify <code>rrdtool:/</code> prefix in the
     * <code>externalPath</code> argument. For example, to create Rrd4j compatible file named
     * <code>copy.rrd</code> from the file <code>original.rrd</code> created with RRDTool, use
     * the following code:</p>
     * <p/>
     * <pre>
     * RrdDb rrd = new RrdDb("copy.rrd", "rrdtool:/original.rrd");
     * </pre>
     * <p/>
     * <p>Note that the prefix <code>xml:/</code> or <code>rrdtool:/</code> is necessary to distinguish
     * between XML and RRDTool's binary sources. If no prefix is supplied, XML format is assumed</p>
     *
     * @param rrdPath      Path to a RRD file which will be created
     * @param externalPath Path to an external file which should be imported, with an optional
     *                     <code>xml:/</code> or <code>rrdtool:/</code> prefix.
     * @throws IOException Thrown in case of I/O error
     */
    public RrdDb(String rrdPath, String externalPath) throws IOException {
        this(rrdPath, externalPath, RrdBackendFactory.getDefaultFactory());
    }

    /**
     * <p>Constructor used to create RRD files from external file sources with a backend type
     * different from default. Supported external file sources are:</p>
     * <p/>
     * <ul>
     * <li>RRDTool/Rrd4j XML file dumps (i.e files created with <code>rrdtool dump</code> command).
     * <li>RRDTool binary files.
     * </ul>
     * <p/>
     * <p>Rrd4j and RRDTool use the same format for XML dump and this constructor should be used to
     * (re)create Rrd4j RRD files from XML dumps. First, dump the content of a RRDTool
     * RRD file (use command line):</p>
     * <p/>
     * <pre>
     * rrdtool dump original.rrd > original.xml
     * </pre>
     * <p/>
     * <p>Than, use the file <code>original.xml</code> to create Rrd4j RRD file named
     * <code>copy.rrd</code>:</p>
     * <p/>
     * <pre>
     * RrdDb rrd = new RrdDb("copy.rrd", "original.xml");
     * </pre>
     * <p/>
     * <p>or:</p>
     * <p/>
     * <pre>
     * RrdDb rrd = new RrdDb("copy.rrd", "xml:/original.xml");
     * </pre>
     * <p/>
     * <p>See documentation for {@link #dumpXml(String) dumpXml()} method
     * to see how to convert Rrd4j files to RRDTool's format.</p>
     * <p/>
     * <p>To read RRDTool files directly, specify <code>rrdtool:/</code> prefix in the
     * <code>externalPath</code> argument. For example, to create Rrd4j compatible file named
     * <code>copy.rrd</code> from the file <code>original.rrd</code> created with RRDTool, use
     * the following code:</p>
     * <p/>
     * <pre>
     * RrdDb rrd = new RrdDb("copy.rrd", "rrdtool:/original.rrd");
     * </pre>
     * <p/>
     * <p>Note that the prefix <code>xml:/</code> or <code>rrdtool:/</code> is necessary to distinguish
     * between XML and RRDTool's binary sources. If no prefix is supplied, XML format is assumed</p>
     *
     * @param rrdPath      Path to RRD which will be created
     * @param externalPath Path to an external file which should be imported, with an optional
     *                     <code>xml:/</code> or <code>rrdtool:/</code> prefix.
     * @param factory      Backend factory which will be used to create storage (backend) for this RRD.
     * @throws IOException Thrown in case of I/O error
     * @see RrdBackendFactory
     */
    public RrdDb(String rrdPath, String externalPath, RrdBackendFactory factory) throws IOException {
        DataImporter reader;
        if (externalPath.startsWith(PREFIX_RRDTool)) {
            String rrdToolPath = externalPath.substring(PREFIX_RRDTool.length());
            reader = new RrdToolReader(rrdToolPath);
        }
        else if (externalPath.startsWith(PREFIX_XML)) {
            externalPath = externalPath.substring(PREFIX_XML.length());
            reader = new XmlReader(externalPath);
        }
        else {
            reader = new XmlReader(externalPath);
        }
        backend = factory.open(rrdPath, false);
        try {
            backend.setLength(reader.getEstimatedSize());
            // create header
            header = new Header(this, reader);
            // create datasources
            datasources = new Datasource[reader.getDsCount()];
            for (int i = 0; i < datasources.length; i++) {
                datasources[i] = new Datasource(this, reader, i);
            }
            // create archives
            archives = new Archive[reader.getArcCount()];
            for (int i = 0; i < archives.length; i++) {
                archives[i] = new Archive(this, reader, i);
            }
            reader.release();

            // XMLReader is a rather huge DOM tree, release memory ASAP
            reader = null;
        }
        catch (IOException e) {
            backend.close();
            throw e;
        }
    }

    /**
     * Closes RRD. No further operations are allowed on this RrdDb object.
     *
     * @throws IOException Thrown in case of I/O related error.
     */
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            backend.close();
        }
    }

    /**
     * Returns true if the RRD is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns RRD header.
     *
     * @return Header object
     */
    public Header getHeader() {
        return header;
    }

    /**
     * Returns Datasource object for the given datasource index.
     *
     * @param dsIndex Datasource index (zero based)
     * @return Datasource object
     */
    public Datasource getDatasource(int dsIndex) {
        return datasources[dsIndex];
    }

    /**
     * Returns Archive object for the given archive index.
     *
     * @param arcIndex Archive index (zero based)
     * @return Archive object
     */
    public Archive getArchive(int arcIndex) {
        return archives[arcIndex];
    }

    /**
     * <p>Returns an array of datasource names defined in RRD.</p>
     *
     * @return Array of datasource names.
     * @throws IOException Thrown in case of I/O error.
     */
    public String[] getDsNames() throws IOException {
        int n = datasources.length;
        String[] dsNames = new String[n];
        for (int i = 0; i < n; i++) {
            dsNames[i] = datasources[i].getName();
        }
        return dsNames;
    }

    /**
     * <p>Creates new sample with the given timestamp and all datasource values set to
     * 'unknown'. Use returned <code>Sample</code> object to specify
     * datasource values for the given timestamp. See documentation for
     * {@link Sample Sample} for an explanation how to do this.</p>
     * <p/>
     * <p>Once populated with data source values, call Sample's
     * {@link Sample#update() update()} method to actually
     * store sample in the RRD associated with it.</p>
     *
     * @param time Sample timestamp rounded to the nearest second (without milliseconds).
     * @return Fresh sample with the given timestamp and all data source values set to 'unknown'.
     * @throws IOException Thrown in case of I/O error.
     */
    public Sample createSample(long time) throws IOException {
        return new Sample(this, time);
    }

    /**
     * <p>Creates new sample with the current timestamp and all data source values set to
     * 'unknown'. Use returned <code>Sample</code> object to specify
     * datasource values for the current timestamp. See documentation for
     * {@link Sample Sample} for an explanation how to do this.</p>
     * <p/>
     * <p>Once populated with data source values, call Sample's
     * {@link Sample#update() update()} method to actually
     * store sample in the RRD associated with it.</p>
     *
     * @return Fresh sample with the current timestamp and all
     *         data source values set to 'unknown'.
     * @throws IOException Thrown in case of I/O error.
     */
    public Sample createSample() throws IOException {
        return createSample(Util.getTime());
    }

    /**
     * <p>Prepares fetch request to be executed on this RRD. Use returned
     * <code>FetchRequest</code> object and its {@link FetchRequest#fetchData() fetchData()}
     * method to actually fetch data from the RRD file.</p>
     *
     * @param consolFun  Consolidation function to be used in fetch request. Allowed values are
     *                   "AVERAGE", "MIN", "MAX" and "LAST" (these constants are conveniently defined in the
     *                   {@link ConsolFun} class).
     * @param fetchStart Starting timestamp for fetch request.
     * @param fetchEnd   Ending timestamp for fetch request.
     * @param resolution Fetch resolution (see RRDTool's
     *                   <a href="../../../../man/rrdfetch.html" target="man">rrdfetch man page</a> for an
     *                   explanation of this parameter.
     * @return Request object that should be used to actually fetch data from RRD
     */
    public FetchRequest createFetchRequest(ConsolFun consolFun, long fetchStart, long fetchEnd, long resolution) {
        return new FetchRequest(this, consolFun, fetchStart, fetchEnd, resolution);
    }

    /**
     * <p>Prepares fetch request to be executed on this RRD. Use returned
     * <code>FetchRequest</code> object and its {@link FetchRequest#fetchData() fetchData()}
     * method to actually fetch data from this RRD. Data will be fetched with the smallest
     * possible resolution (see RRDTool's
     * <a href="../../../../man/rrdfetch.html" target="man">rrdfetch man page</a>
     * for the explanation of the resolution parameter).</p>
     *
     * @param consolFun  Consolidation function to be used in fetch request. Allowed values are
     *                   AVERAGE, MIN, MAX, FIRST, LAST and TOTAL (see {@link ConsolFun} enum).
     * @param fetchStart Starting timestamp for fetch request.
     * @param fetchEnd   Ending timestamp for fetch request.
     * @return Request object that should be used to actually fetch data from RRD.
     */
    public FetchRequest createFetchRequest(ConsolFun consolFun, long fetchStart, long fetchEnd) {
        return createFetchRequest(consolFun, fetchStart, fetchEnd, 1);
    }

    final synchronized void store(Sample sample) throws IOException {
        if (closed) {
            throw new IllegalStateException("RRD already closed, cannot store this sample");
        }
        long newTime = sample.getTime();
        long lastTime = header.getLastUpdateTime();
        if (lastTime >= newTime) {
            throw new IllegalArgumentException("Bad sample time: " + newTime +
                    ". Last update time was " + lastTime + ", at least one second step is required");
        }
        double[] newValues = sample.getValues();
        for (int i = 0; i < datasources.length; i++) {
            double newValue = newValues[i];
            datasources[i].process(newTime, newValue);
        }
        header.setLastUpdateTime(newTime);
    }

    synchronized FetchData fetchData(FetchRequest request) throws IOException {
        if (closed) {
            throw new IllegalStateException("RRD already closed, cannot fetch data");
        }
        Archive archive = findMatchingArchive(request);
        return archive.fetchData(request);
    }

    public Archive findMatchingArchive(FetchRequest request) throws IOException {
        ConsolFun consolFun = request.getConsolFun();
        long fetchStart = request.getFetchStart();
        long fetchEnd = request.getFetchEnd();
        long resolution = request.getResolution();
        Archive bestFullMatch = null, bestPartialMatch = null;
        long bestStepDiff = 0, bestMatch = 0;
        for (Archive archive : archives) {
            if (archive.getConsolFun() == consolFun) {
                long arcStep = archive.getArcStep();
                long arcStart = archive.getStartTime() - arcStep;
                long arcEnd = archive.getEndTime();
                long fullMatch = fetchEnd - fetchStart;
                if (arcEnd >= fetchEnd && arcStart <= fetchStart) {
                    long tmpStepDiff = Math.abs(archive.getArcStep() - resolution);

                    if (tmpStepDiff < bestStepDiff || bestFullMatch == null) {
                        bestStepDiff = tmpStepDiff;
                        bestFullMatch = archive;
                    }
                }
                else {
                    long tmpMatch = fullMatch;

                    if (arcStart > fetchStart) {
                        tmpMatch -= (arcStart - fetchStart);
                    }
                    if (arcEnd < fetchEnd) {
                        tmpMatch -= (fetchEnd - arcEnd);
                    }
                    if (bestPartialMatch == null || bestMatch < tmpMatch) {
                        bestPartialMatch = archive;
                        bestMatch = tmpMatch;
                    }
                }
            }
        }
        if (bestFullMatch != null) {
            return bestFullMatch;
        }
        else if (bestPartialMatch != null) {
            return bestPartialMatch;
        }
        else {
            throw new IllegalStateException("RRD file does not contain RRA: " + consolFun + " archive");
        }
    }

    /**
     * Finds the archive that best matches to the start time (time period being start-time until now)
     * and requested resolution.
     *
     * @param consolFun  Consolidation function of the datasource.
     * @param startTime  Start time of the time period in seconds.
     * @param resolution Requested fetch resolution.
     * @return Reference to the best matching archive.
     * @throws IOException Thrown in case of I/O related error.
     */
    public Archive findStartMatchArchive(String consolFun, long startTime, long resolution) throws IOException {
        long arcStep, diff;
        int fallBackIndex = 0;
        int arcIndex = -1;
        long minDiff = Long.MAX_VALUE;
        long fallBackDiff = Long.MAX_VALUE;

        for (int i = 0; i < archives.length; i++) {
            if (archives[i].getConsolFun().toString().equals(consolFun)) {
                arcStep = archives[i].getArcStep();
                diff = Math.abs(resolution - arcStep);

                // Now compare start time, see if this archive encompasses the requested interval
                if (startTime >= archives[i].getStartTime()) {
                    if (diff == 0)                // Best possible match either way
                    {
                        return archives[i];
                    }
                    else if (diff < minDiff) {
                        minDiff = diff;
                        arcIndex = i;
                    }
                }
                else if (diff < fallBackDiff) {
                    fallBackDiff = diff;
                    fallBackIndex = i;
                }
            }
        }

        return (arcIndex >= 0 ? archives[arcIndex] : archives[fallBackIndex]);
    }

    /**
     * <p>Returns string representing complete internal RRD state. The returned
     * string can be printed to <code>stdout</code> and/or used for debugging purposes.</p>
     *
     * @return String representing internal RRD state.
     * @throws IOException Thrown in case of I/O related error.
     */
    public synchronized String dump() throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(header.dump());
        for (Datasource datasource : datasources) {
            buffer.append(datasource.dump());
        }
        for (Archive archive : archives) {
            buffer.append(archive.dump());
        }
        return buffer.toString();
    }

    final void archive(Datasource datasource, double value, long numUpdates) throws IOException {
        int dsIndex = getDsIndex(datasource.getName());
        for (Archive archive : archives) {
            archive.archive(dsIndex, value, numUpdates);
        }
    }

    /**
     * Returns internal index number for the given datasource name.
     *
     * @param dsName Data source name.
     * @return Internal index of the given data source name in this RRD.
     * @throws IOException Thrown in case of I/O error.
     */
    public int getDsIndex(String dsName) throws IOException {
        for (int i = 0; i < datasources.length; i++) {
            if (datasources[i].getName().equals(dsName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown datasource name: " + dsName);
    }

    /**
     * Checks presence of a specific datasource.
     *
     * @param dsName Datasource name to check
     * @return <code>true</code> if datasource is present in this RRD, <code>false</code> otherwise
     * @throws IOException Thrown in case of I/O error.
     */
    public boolean containsDs(String dsName) throws IOException {
        for (Datasource datasource : datasources) {
            if (datasource.getName().equals(dsName)) {
                return true;
            }
        }
        return false;
    }

    Datasource[] getDatasources() {
        return datasources;
    }

    Archive[] getArchives() {
        return archives;
    }

    /**
     * <p>Writes the RRD content to OutputStream using XML format. This format
     * is fully compatible with RRDTool's XML dump format and can be used for conversion
     * purposes or debugging.</p>
     *
     * @param destination Output stream to receive XML data
     * @throws IOException Thrown in case of I/O related error
     */
    public synchronized void dumpXml(OutputStream destination) throws IOException {
        XmlWriter writer = new XmlWriter(destination);
        writer.startTag("rrd");
        // dump header
        header.appendXml(writer);
        // dump datasources
        for (Datasource datasource : datasources) {
            datasource.appendXml(writer);
        }
        // dump archives
        for (Archive archive : archives) {
            archive.appendXml(writer);
        }
        writer.closeTag();
        writer.flush();
    }

    /**
     * This method is just an alias for {@link #dumpXml(OutputStream) dumpXml} method.
     *
     * @throws IOException Thrown in case of I/O related error
     */
    public synchronized void exportXml(OutputStream destination) throws IOException {
        dumpXml(destination);
    }

    /**
     * <p>Returns string representing internal RRD state in XML format. This format
     * is fully compatible with RRDTool's XML dump format and can be used for conversion
     * purposes or debugging.</p>
     *
     * @return Internal RRD state in XML format.
     * @throws IOException Thrown in case of I/O related error
     */
    public synchronized String getXml() throws IOException {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(XML_BUFFER_CAPACITY);
        dumpXml(destination);
        return destination.toString();
    }

    /**
     * This method is just an alias for {@link #getXml() getXml} method.
     *
     * @return Internal RRD state in XML format.
     * @throws IOException Thrown in case of I/O related error
     */
    public synchronized String exportXml() throws IOException {
        return getXml();
    }

    /**
     * Dumps internal RRD state to XML file.
     * Use this XML file to convert your Rrd4j RRD to RRDTool format.
     *
     * <p>Suppose that you have a Rrd4j RRD file <code>original.rrd</code> and you want
     * to convert it to RRDTool format. First, execute the following java code:</p>
     *
     * <code>RrdDb rrd = new RrdDb("original.rrd");
     * rrd.dumpXml("original.xml");</code>
     *
     * <p>Use <code>original.xml</code> file to create the corresponding RRDTool file
     * (from your command line):
     *
     * <code>rrdtool restore copy.rrd original.xml</code>
     *
     * @param filename Path to XML file which will be created.
     * @throws IOException Thrown in case of I/O related error.
     */
    public synchronized void dumpXml(String filename) throws IOException {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(filename, false);
            dumpXml(outputStream);
        }
        finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    /**
     * This method is just an alias for {@link #dumpXml(String) dumpXml(String)} method.
     *
     * @throws IOException Thrown in case of I/O related error
     */
    public synchronized void exportXml(String filename) throws IOException {
        dumpXml(filename);
    }

    /**
     * Returns time of last update operation as timestamp (in seconds).
     *
     * @return Last update time (in seconds).
     */
    public synchronized long getLastUpdateTime() throws IOException {
        return header.getLastUpdateTime();
    }

    /**
     * <p>Returns RRD definition object which can be used to create new RRD
     * with the same creation parameters but with no data in it.</p>
     * <p/>
     * <p>Example:</p>
     * <p/>
     * <pre>
     * RrdDb rrd1 = new RrdDb("original.rrd");
     * RrdDef def = rrd1.getRrdDef();
     * // fix path
     * def.setPath("empty_copy.rrd");
     * // create new RRD file
     * RrdDb rrd2 = new RrdDb(def);
     * </pre>
     *
     * @return RRD definition.
     */
    public synchronized RrdDef getRrdDef() throws IOException {
        // set header
        long startTime = header.getLastUpdateTime();
        long step = header.getStep();
        int version = header.getVersion();
        String path = backend.getPath();
        RrdDef rrdDef = new RrdDef(path, startTime, step, version);
        // add datasources
        for (Datasource datasource : datasources) {
            DsDef dsDef = new DsDef(datasource.getName(),
                    datasource.getType(), datasource.getHeartbeat(),
                    datasource.getMinValue(), datasource.getMaxValue());
            rrdDef.addDatasource(dsDef);
        }
        // add archives
        for (Archive archive : archives) {
            ArcDef arcDef = new ArcDef(archive.getConsolFun(),
                    archive.getXff(), archive.getSteps(), archive.getRows());
            rrdDef.addArchive(arcDef);
        }
        return rrdDef;
    }

    protected void finalize() throws Throwable {
        try {
            close();
        }
        finally {
            super.finalize();
        }
    }

    /**
     * Copies object's internal state to another RrdDb object.
     *
     * @param other New RrdDb object to copy state to
     * @throws IOException Thrown in case of I/O error
     */
    public synchronized void copyStateTo(RrdUpdater other) throws IOException {
        if (!(other instanceof RrdDb)) {
            throw new IllegalArgumentException("Cannot copy RrdDb object to " + other.getClass().getName());
        }
        RrdDb otherRrd = (RrdDb) other;
        header.copyStateTo(otherRrd.header);
        for (int i = 0; i < datasources.length; i++) {
            int j = Util.getMatchingDatasourceIndex(this, i, otherRrd);
            if (j >= 0) {
                datasources[i].copyStateTo(otherRrd.datasources[j]);
            }
        }
        for (int i = 0; i < archives.length; i++) {
            int j = Util.getMatchingArchiveIndex(this, i, otherRrd);
            if (j >= 0) {
                archives[i].copyStateTo(otherRrd.archives[j]);
            }
        }
    }

    /**
     * Returns Datasource object corresponding to the given datasource name.
     *
     * @param dsName Datasource name
     * @return Datasource object corresponding to the give datasource name or null
     *         if not found.
     * @throws IOException Thrown in case of I/O error
     */
    public Datasource getDatasource(String dsName) throws IOException {
        try {
            return getDatasource(getDsIndex(dsName));
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns index of Archive object with the given consolidation function and the number
     * of steps. Exception is thrown if such archive could not be found.
     *
     * @param consolFun Consolidation function
     * @param steps     Number of archive steps
     * @return Requested Archive object
     * @throws IOException Thrown in case of I/O error
     */
    public int getArcIndex(ConsolFun consolFun, int steps) throws IOException {
        for (int i = 0; i < archives.length; i++) {
            if (archives[i].getConsolFun() == consolFun && archives[i].getSteps() == steps) {
                return i;
            }
        }
        throw new IllegalArgumentException("Could not find archive " + consolFun + "/" + steps);
    }

    /**
     * Returns Archive object with the given consolidation function and the number
     * of steps.
     *
     * @param consolFun Consolidation function
     * @param steps     Number of archive steps
     * @return Requested Archive object or null if no such archive could be found
     * @throws IOException Thrown in case of I/O error
     */
    public Archive getArchive(ConsolFun consolFun, int steps) throws IOException {
        try {
            return getArchive(getArcIndex(consolFun, steps));
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns canonical path to the underlying RRD file. Note that this method makes sense just for
     * ordinary RRD files created on the disk - an exception will be thrown for RRD objects created in
     * memory or with custom backends.
     *
     * @return Canonical path to RRD file;
     * @throws IOException Thrown in case of I/O error or if the underlying backend is
     *                     not derived from RrdFileBackend.
     */
    public String getCanonicalPath() throws IOException {
        if (backend instanceof RrdFileBackend) {
            return ((RrdFileBackend) backend).getCanonicalPath();
        }
        else {
            throw new IOException("The underlying backend has no canonical path");
        }
    }

    /**
     * Returns path to this RRD.
     *
     * @return Path to this RRD.
     */
    public String getPath() {
        return backend.getPath();
    }

    /**
     * Returns backend object for this RRD which performs actual I/O operations.
     *
     * @return RRD backend for this RRD.
     */
    public RrdBackend getRrdBackend() {
        return backend;
    }

    /**
     * Required to implement RrdUpdater interface. You should never call this method directly.
     *
     * @return Allocator object
     */
    public RrdAllocator getRrdAllocator() {
        return allocator;
    }

    /**
     * Returns an array of bytes representing the whole RRD.
     *
     * @return All RRD bytes
     * @throws IOException Thrown in case of I/O related error.
     */
    public synchronized byte[] getBytes() throws IOException {
        return backend.readAll();
    }

    /**
     * Sets default backend factory to be used. This method is just an alias for
     * {@link RrdBackendFactory#setDefaultFactory(String)}.<p>
     *
     * @param factoryName Name of the backend factory to be set as default.
     * @throws IllegalArgumentException Thrown if invalid factory name is supplied, or not called
     *                                  before the first backend object (before the first RrdDb object) is created.
     */
    public static void setDefaultFactory(String factoryName) {
        RrdBackendFactory.setDefaultFactory(factoryName);
    }

    /**
     * Returns an array of last datasource values. The first value in the array corresponds
     * to the first datasource defined in the RrdDb and so on.
     *
     * @return Array of last datasource values
     * @throws IOException Thrown in case of I/O error
     */
    public synchronized double[] getLastDatasourceValues() throws IOException {
        double[] values = new double[datasources.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = datasources[i].getLastValue();
        }
        return values;
    }

    /**
     * Returns the last stored value for the given datasource.
     *
     * @param dsName Datasource name
     * @return Last stored value for the given datasource
     * @throws IOException              Thrown in case of I/O error
     * @throws IllegalArgumentException Thrown if no datasource in this RrdDb matches the given datasource name
     */
    public synchronized double getLastDatasourceValue(String dsName) throws IOException {
        int dsIndex = getDsIndex(dsName);
        return datasources[dsIndex].getLastValue();
    }

    /**
     * Returns the number of datasources defined in the file
     *
     * @return The number of datasources defined in the file
     */
    public int getDsCount() {
        return datasources.length;
    }

    /**
     * Returns the number of RRA archives defined in the file
     *
     * @return The number of RRA archives defined in the file
     */
    public int getArcCount() {
        return archives.length;
    }

    /**
     * Returns the last time when some of the archives in this RRD was updated. This time is not the
     * same as the {@link #getLastUpdateTime()} since RRD file can be updated without updating any of
     * the archives.
     *
     * @return last time when some of the archives in this RRD was updated
     * @throws IOException Thrown in case of I/O error
     */
    public long getLastArchiveUpdateTime() throws IOException {
        long last = 0;
        for (Archive archive : archives) {
            last = Math.max(last, archive.getEndTime());
        }
        return last;
    }

    public synchronized String getInfo() throws IOException {
        return header.getInfo();
    }

    public synchronized void setInfo(String info) throws IOException {
        header.setInfo(info);
    }

    public static void main(String[] args) {
        System.out.println("RRD4J :: RRDTool choice for the Java world");
        System.out.println("===============================================================================");
        System.out.println("RRD4J base directory: " + Util.getRrd4jHomeDirectory());
        long time = Util.getTime();
        System.out.println("Current time: " + time + ": " + new Date(time * 1000L));
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("See http://code.google.com/p/rrd4j/ for more information and the latest version.");
        System.out.println("Copyright 2011 Sasa Markovic and Mathias Bogaert. Licensed under the Apache License, Version 2.0.");
    }

}
