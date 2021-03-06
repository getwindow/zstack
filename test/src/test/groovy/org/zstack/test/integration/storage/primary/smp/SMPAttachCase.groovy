package org.zstack.test.integration.storage.primary.smp

import org.springframework.http.HttpEntity
import org.zstack.header.storage.primary.PrimaryStorageVO
import org.zstack.sdk.AttachPrimaryStorageToClusterAction
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.storage.primary.smp.KvmBackend
import org.zstack.test.integration.storage.SMPEnv
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.HostSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.gson.JSONObjectUtil

import java.util.concurrent.TimeUnit

/**
 * Created by AlanJager on 2017/3/10.
 */
class SMPAttachCase extends SubCase{
    EnvSpec env

    @Override
    void setup() {
        useSpring(StorageTest.springSpec)
    }

    @Override
    void environment() {
        env = SMPEnv.twoHostsNoVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testAttachingSmpWithoutMountPathOnHost()
        }
    }

    void testAttachingSmpWithoutMountPathOnHost() {
        PrimaryStorageInventory primaryStorageInventory = env.inventoryByName("smp")
        ClusterInventory clusterInventory = env.inventoryByName("cluster")
        HostSpec hostSpec1 = env.specByName("kvm1")
        HostSpec hostSpec2 = env.specByName("kvm2")
        HostSpec hostSpec3 = env.specByName("kvm3")

        detachPrimaryStorageFromCluster {
            primaryStorageUuid = primaryStorageInventory.uuid
            clusterUuid = clusterInventory.uuid
        }
        TimeUnit.SECONDS.sleep(3)

        def counter = 0 as int
        KvmBackend.ConnectCmd cmd = null
        env.afterSimulator(KvmBackend.CONNECT_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, KvmBackend.ConnectCmd.class)
            def ret = new KvmBackend.AgentRsp()
            if(counter > 0) {
                ret.success = false
                ret.error = "failed"
            } else {
                ret.success = true
            }
            counter++
            return ret
        }

        AttachPrimaryStorageToClusterAction action = new AttachPrimaryStorageToClusterAction()
        action.clusterUuid = clusterInventory.uuid
        action.primaryStorageUuid = primaryStorageInventory.uuid
        action.sessionId = adminSession()
        AttachPrimaryStorageToClusterAction.Result ret = action.call()
        assert ret.error != null

        PrimaryStorageVO vo = dbFindByUuid(primaryStorageInventory.uuid, PrimaryStorageVO.class)
        retryInSecs(3) {
            vo = dbFindByUuid(primaryStorageInventory.uuid, PrimaryStorageVO.class)
            return {
                vo.getAttachedClusterRefs().isEmpty()
                cmd != null
            }
        }
    }

    @Override
    void clean() {
        env.delete()
    }
}
