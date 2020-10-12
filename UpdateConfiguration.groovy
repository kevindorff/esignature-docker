/**
 * Update the eSignature configuration stored in .env and
 * localApplication.properties based on the system's hostname and the IP
 * address. This will prefer the VPN IP address, if found.
 *
 * Exit codes:
 *   -1 configuration file could not be written
 *   -2 could not determine hostname
 *   -3 could not determine ip address
 */

class UpdateConfiguration {

  /**
   * Work starts here.
   */
  void run() {
    println ""
    IpConfig ip = getIpAddess()
    if (!ip.isVpn) {
      println "WARNING: It seems you are NOT on the VPN."
      println "WARNING: If this is a first time setup, you may not"
      println "WARNING: be able  to create the signature container."
      println ""
    }
    println "IP is ${ip.ip} (${ip.isVpn ? "VPN" : "NOT on the VPN"})"

    String hostname = getHostname()
    println "Hostname is ${hostname}"

    makeFilesFromTemplates([
      "__VPN_IP__": ip.ip,
      "__LOCAL_HOSTNAME__": hostname
    ])
  }

  /**
   * Get the local hostname.
   * If not obtainable, the application will exit with -2.
   *
   * @return the hostname as a String. 
   */
  String getHostname() {
    String hostname = java.net.InetAddress.getLocalHost().getHostName()
    if (!hostname) {
      System.err.println("Couldn't determine hostname")
      System.err.println("Exiting.")
      System.exit(-2)
    }
    return hostname
  }

  /**
   * Use `ipconfig` to find the IP address of the VPN (preferably)
   * otherwise the non-VPN ip address.
   * If not obtainable, the application will exit with -3.
   *
   * @return The IPv4 Address, preferably for the VPN
   */
  IpConfig getIpAddess() {
    List<Map> configs = parseIpConfig()

    /**
     * This code may not fully cover every scenario.
     * If this isn't detecting your IP when you are
     * and aren't on the VPN, we need to update this code.
     */
    boolean isVpn = true
    String localOrVpnIp = findIpAddress(configs, "corp.peopleclick.com")
    if (!localOrVpnIp) {
      // Look for a non-VPN IP address
      isVpn = false
      localOrVpnIp = findIpAddress(configs, "local")
    }

    if (!localOrVpnIp) {
      System.err.println("Could not detect IP address. Check script logic.")
      System.err.println("Exiting.")
      System.exit(-3)
    }
    return new IpConfig(ip: localOrVpnIp, isVpn: isVpn)
  }

  class IpConfig {
    String ip
    boolean isVpn
  }

  /**
   * Execute ipconfig and parse the data into a List of configs.
   * Each config is a map.
   */
  List<Map> parseIpConfig() {
    String cmd = "ipconfig"
    List<Map> configs = []
    Map currentConfig = null

    // Parse output result of ipconfig into configs
    cmd.execute().text.split("[\n\r]").
      collect { String line ->
        line.trim() 
      }.
      each { String line ->
        if (!line) { return }
        else if (line.startsWith("Ethernet adapter ") || line.startsWith("Wireless LAN adapter")) {
          currentConfig = ['name': line]
          configs << currentConfig
        }
        else if (!currentConfig) { return }
        else {
          LineParts parts = splitDataLine(line)
          if (parts) {
            currentConfig[parts.key] = parts.value
          }
        }
      }
    return configs
  }

  class LineParts {
    String key
    String value
  }

  /**
   * A key/value line from ipconfig (within an adapter stanza) looks like
   * "IPv4 Address. . . . . . . . . . . : 192.168.1.153"
   * Split this into a LineParts with
   *    key: "IPv4 Address"
   *    value: "192.168.1.153"
   * If this format is not found, return null
   *
   * @return a LineParts or null
   */
  LineParts splitDataLine(String line) {
    def result = (line =~ /^(.*?)( ?\.?)* :[ ]?(.*)$/).findAll()
    if (result.size() == 1) {
      return new LineParts(key: result[0][1], value: result[0][3])
    }
    else {
      return null
    }
  }

  /**
   * Return the 'IPv4 Address' for a config with 
   * 'Connection-specific DNS Suffix' == dnsSuffixPriorty
   * or null.
   * 
   * @return the IPv4 address or null
   */
  String findIpAddress(List<Map> configs, String dnsSuffixPriorty) {
    Map foundConfig = configs.find { Map config ->
      config.'Connection-specific DNS Suffix' == dnsSuffixPriorty &&
        config.containsKey('IPv4 Address')
    }
    return foundConfig ? foundConfig.'IPv4 Address' : null
  }

  /**
   * Map of template files to use to create
   * usable files. Key is source template file,
   * value is output file to be created.
   */
  Map templates = [
    (new File('localApplication.template')) : (new File('localApplication.properties')),
    (new File('env.template')) : (new File('.env')),
  ]

  /**
   * Create usable files from the template files,
   * substituting config values.
   * If any of the source template files don't exist, 
   * the application will exit with -1.
   */
  void makeFilesFromTemplates(Map substitutions) {
    println ""
    templates.each { File sourceFile, File destFile ->
      if (!sourceFile.exists()) {
        System.err.println("Source template file ${sourceFile} missing")
        System.err.println("Exiting.")
        System.exit(-1)
      }

      destFile.delete()
      String output = sourceFile.text
      substitutions.each { subKey, subValue ->
        output = output.replaceAll(subKey, subValue)
      }
      destFile << output
      println "Created file from template ${destFile}"
    }
  }

  /**
   * Script entry point.
   */
  static void main(String[] args) {
    new UpdateConfiguration().run()
  }
}
