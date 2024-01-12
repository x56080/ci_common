#!/bin/bash

# Function to display help
function display_help() {
    echo "Usage: $0 --hostlist <host1,host2,host3> --ccdir <ccdir> --conf <conf_file>"
    echo ""
    echo "Options:"
    echo "  --hostlist      Comma-separated list of hosts"
    echo "  --ccdir         Directory for ccPath"
    echo "  --conf          Configuration file path"
    echo "  -h, --help      Display this help message"
}

# Function to generate configuration file
function generate_config() {
    local hostlist="$1"
    local ccdir="$2"
    local conf_file="$3"

    IFS=',' read -ra hosts <<< "$hostlist"

    # Generate [CcConfig] section
    echo "[CcConfig]" > "$conf_file"
    for ((i = 0; i < ${#hosts[@]}; i++)); do
        echo "host$((i + 1))=${hosts[i]}" >> "$conf_file"
    done

    tar -xzvf $(ls ${ccdir}/sdb-dds-cc_*.tar.gz) -C ${ccdir}
    ccpos=$(ls ${ccdir}/sdb-dds-cc_*/sdb-dds-cc)
    echo "globalUser=sdbadmin" >> "$conf_file"
    echo "ccPath=${ccpos}" >> "$conf_file"

    # Generate [Ctl] section
    echo "[Ctl]" >> "$conf_file"
    echo "globalUser=sdbadmin" >> "$conf_file"
}

# Parse command-line arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --hostlist) hostlist="$2"; shift ;;
        --ccdir) ccdir="$2"; shift ;;
        --conf) conf_file="$2"; shift ;;
        -h | --help) display_help; exit 0 ;;
        *) echo "Unknown option: $1"; display_help; exit 1 ;;
    esac
    shift
done

# Check if required arguments are provided
if [[ -z "$hostlist" || -z "$ccdir" || -z "$conf_file" ]]; then
    echo "Error: Missing required arguments."
    display_help
    exit 1
fi


# Check if ccdir exists
if [ ! -d "$ccdir" ]; then
    echo "Error: ccdir does not exist: $ccdir"
    exit 1
fi

# Check if conf_file exists, clear or create accordingly
if [ -f "$conf_file" ]; then
    >"$conf_file"
else
    mkdir -p $(dirname $conf_file)
    touch "$conf_file"
fi

# Generate configuration file
generate_config "$hostlist" "$ccdir" "$conf_file"
echo "Configuration file generated successfully: $conf_file"

