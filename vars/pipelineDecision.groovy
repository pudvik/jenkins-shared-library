#!groovy

// declaring function

def decidePipeline(Map, configMap) {
    type: configMap.get("type")
    switch(type) {
        case nodeJSEKS:
            nodeJSEKS(configMap)   
            break
        case nodeJSVM:
            nodeJSVM(configMap)
            break
        default:
            error "type is not matched"
            break
    }
}