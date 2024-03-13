import lark_oapi as lark
from lark_oapi.api.drive.v1 import *
from lark_oapi.api.docx.v1 import *
from lark_oapi.api.wiki.v2 import *
import datetime
import argparse

# SDK 使用说明: https://github.com/larksuite/oapi-sdk-python#readme

def initialize_client():
    # 创建client
    # 使用 user_access_token 需开启 token 配置, 并在 request_option 中配置 token
    client = lark.Client.builder() \
        .enable_set_token(True) \
        .log_level(lark.LogLevel.DEBUG) \
        .build()
    return client

def copy_file(client, user_access_token, file_token, name, folder_token):
    # 构造请求对象
    request: CopyFileRequest = CopyFileRequest.builder() \
        .file_token(file_token) \
        .request_body(CopyFileRequestBody.builder()
            .name(name)
            .type("docx")
            .folder_token(folder_token)
            .build()) \
        .build()

    # 发起请求
    option = lark.RequestOption.builder().user_access_token(user_access_token).build()
    response: CopyFileResponse = client.drive.v1.file.copy(request, option)

    # 处理失败返回
    if not response.success():
        lark.logger.error(
            f"client.drive.v1.file.copy failed, code: {response.code}, msg: {response.msg}, log_id: {response.get_log_id()}")
        return None

    # 处理业务结果
    lark.logger.info(lark.JSON.marshal(response.data, indent=4))
    return response.data.file.token
  

def get_all_document_blocks(client, document_id, user_access_token):
    # 构造请求对象
    request: ListDocumentBlockRequest = ListDocumentBlockRequest.builder() \
        .document_id(document_id) \
        .page_size(500) \
        .document_revision_id(-1) \
        .build()

    # 发起请求
    option = lark.RequestOption.builder().user_access_token(user_access_token).build()
    response: ListDocumentBlockResponse = client.docx.v1.document_block.list(request, option)

    # 处理失败返回
    if not response.success():
        lark.logger.error(
            f"client.docx.v1.document_block.list failed, code: {response.code}, msg: {response.msg}, log_id: {response.get_log_id()}")
        return None

    # 处理业务结果
    lark.logger.info(lark.JSON.marshal(response.data, indent=4))
    return response.data


def update_block_content(client, document_id, block_id, update_text_elements_request, user_access_token):
    # 构造请求对象
    request: PatchDocumentBlockRequest = PatchDocumentBlockRequest.builder() \
        .document_id(document_id) \
        .block_id(block_id) \
        .document_revision_id(-1) \
        .request_body(update_text_elements_request) \
        .build()

    # 发起请求
    option = lark.RequestOption.builder().user_access_token(user_access_token).build()
    response: PatchDocumentBlockResponse = client.docx.v1.document_block.patch(request, option)

    # 处理失败返回
    if not response.success():
        lark.logger.error(
            f"client.docx.v1.document_block.patch failed, code: {response.code}, msg: {response.msg}, log_id: {response.get_log_id()}")
        return None

    # 处理业务结果
    lark.logger.info(lark.JSON.marshal(response.data, indent=4))
    return response.data

def replace_variables(content, version):
    newcontent = content
    if newcontent:
        if newcontent.find("VERSION") != -1:
            newcontent = newcontent.replace("${VERSION}", version)
            newcontent = newcontent.replace("%24%7BVERSION%7D", version)
        if newcontent.find("DATE") != -1:
            current_date = datetime.datetime.now().strftime("%Y.%m.%d")
            newcontent = newcontent.replace("${DATE}", current_date)
        if newcontent.find("MAJVERSION") != -1:
            if version.count(".") == 2: 
                majversion = version[0:version.rfind('.')]
            else:
                majversion = version
            newcontent = newcontent.replace("${MAJVERSION}", majversion)
            newcontent = newcontent.replace("%24%7BMAJVERSION%7D", majversion)

    return newcontent
	
def update_all_document_blocks(client, document_id, version, user_access_token):
    # 获取所有文档块
    all_blocks = get_all_document_blocks(client, document_id, user_access_token)

    # 检查是否成功获取块
    if all_blocks is None:
        lark.logger.error("Failed to get all document blocks.")
        return None
		
    for block in all_blocks.items:
        block_id = block.block_id
        block_type = block.block_type

        # 处理不同的 block_type
        if block_type == 1:
            # 处理 block_type 为 1 的块
            # 这里可以根据实际需要进行处理
            pass
        elif block_type >= 3 and block_type <=10:
            # 处理 block_type 为 3 的块
            if block_type == 3:
               elements = block.heading1.elements
            elif block_type == 4:
               elements = block.heading2.elements
            elif block_type == 5:
               elements = block.heading3.elements
            elif block_type == 6:
               elements = block.heading4.elements
            elif block_type == 7:
               elements = block.heading5.elements
            elif block_type == 8:
               elements = block.heading6.elements
            elif block_type == 9:
               elements = block.heading7.elements
               
            updated_text_elements = []
            # 遍历 elements 数组中的 text_run
            for element in elements:
                text_run = element.text_run
                content = text_run.content
                
                text_element_style = text_run.text_element_style
                
                updated_content = replace_variables(content, version)
                # 构造回 text_run
                updated_text_elements.append( element.builder()
                                             .text_run(text_run.builder()
                                              .content(updated_content)
                                              .text_element_style(text_element_style.builder()
                                                 .bold(text_element_style.bold)
                                                 .italic(text_element_style.italic)
                                                 .strikethrough(text_element_style.strikethrough)
                                                 .underline(text_element_style.underline)
                                                 .inline_code(text_element_style.inline_code)
                                                 .build())
                                                .build())
                                                .build())
            # 在这里构造你的更新请求，可以通过解析 JSON 中的信息来构建
            request = UpdateBlockRequest.builder().update_text_elements(UpdateTextElementsRequest.builder().elements(updated_text_elements).build()).build()
            # 调用更新方法
            update_result = update_block_content(client, document_id, block_id, request, user_access_token)

            # 检查是否更新成功
            if update_result is None:
               lark.logger.error(f"Failed to update block with ID: {block_id}")
            else:
               lark.logger.info(f"Block with ID {block_id} updated successfully.")
        elif block_type == 2:
            # 处理 block_type 为 2 的块
            text_elements = block.text.elements

            # 遍历 text_elements 数组中的 text_run
            updated_text_elements = []
            for element in text_elements:
                text_run = element.text_run
                content = text_run.content

                # 处理 content 中的变量替换
                updated_content = replace_variables(content, version)

                text_element_style = text_run.text_element_style
                # 处理 link.url 中的变量替换
                link_url = None
                if text_element_style.link:
                    link_url = text_element_style.link.url
                    updated_link_url = replace_variables(link_url, version)
               
                if link_url:
                    updated_text_elements.append( element.builder()
                                             .text_run(text_run.builder()
                                              .content(updated_content)
                                              .text_element_style(text_element_style.builder()
                                                 .bold(text_element_style.bold)
                                                 .italic(text_element_style.italic)
                                                 .strikethrough(text_element_style.strikethrough)
                                                 .underline(text_element_style.underline)
                                                 .inline_code(text_element_style.inline_code)
                                                 .link(Link.builder()
                                                     .url(updated_link_url)
                                                     .build())
                                                 .build())
                                                .build())
                                                .build())
                else:
                    updated_text_elements.append( element.builder()
                                             .text_run(text_run.builder()
                                              .content(updated_content)
                                              .text_element_style(text_element_style.builder()
                                                 .bold(text_element_style.bold)
                                                 .italic(text_element_style.italic)
                                                 .strikethrough(text_element_style.strikethrough)
                                                 .underline(text_element_style.underline)
                                                 .inline_code(text_element_style.inline_code)
                                                 .build())
                                                .build())
                                                .build())

            
            request = UpdateBlockRequest.builder().update_text_elements(UpdateTextElementsRequest.builder().elements(updated_text_elements).build()).build()

            # 调用更新方法
            update_result = update_block_content(client, document_id, block_id, request, user_access_token)

            # 检查是否更新成功
            if update_result is None:
                lark.logger.error(f"Failed to update block with ID: {block_id}")
            else:
                lark.logger.info(f"Block with ID {block_id} updated successfully.")
                
def get_wiki_token(client, space_id, parent_node_token, product_name, user_access_token):
    name2wikititle = {"sequoiadb":"SequoiaDB", "sequoiacm":"SequoiaCM", "sequoiasac":"SAC", "sequoiadds":"DDS", "cc":"cluster-config", "m2s":"m2s"}
    if name2wikititle.get(product_name) is None:
        return None
        
    if parent_node_token is None:
        request: ListSpaceNodeRequest = ListSpaceNodeRequest.builder() \
            .space_id(space_id) \
            .build()
    else:
        request: ListSpaceNodeRequest = ListSpaceNodeRequest.builder() \
            .space_id(space_id) \
            .parent_node_token(parent_node_token) \
            .build()

    # 发起请求
    option = lark.RequestOption.builder().user_access_token(user_access_token).build()
    response: ListSpaceNodeResponse = client.wiki.v2.space_node.list(request, option)

    # 处理失败返回
    if not response.success():
        lark.logger.error(
            f"client.wiki.v2.space_node.list failed, code: {response.code}, msg: {response.msg}, log_id: {response.get_log_id()}")
        return None
    
    new_parent_node_token = None
    for item in response.data.items:
       if parent_node_token is None:
          new_parent_node_token = item.node_token;
          break;
       if item.title == name2wikititle[product_name]:
          return item.node_token
       if item.title == "工具包":
          new_parent_node_token = item.node_token;
    if new_parent_node_token is None:
       return None
    
    return get_wiki_token(client, space_id, new_parent_node_token, product_name, user_access_token)
                

def move_docs_to_wikispace(client, space_id, file_token, user_access_token, product_name):
    wiki_token = get_wiki_token(client, space_id,None, product_name, user_access_token)
    if wiki_token is None:
        return 
        
    # 构造请求对象
    request: MoveDocsToWikiSpaceNodeRequest = MoveDocsToWikiSpaceNodeRequest.builder() \
        .space_id(space_id) \
        .request_body(MoveDocsToWikiSpaceNodeRequestBody.builder()
            .parent_wiki_token(wiki_token)
            .obj_type("docx")
            .obj_token(file_token)
            .apply(True)
            .build()) \
        .build()

    # 发起请求
    option = lark.RequestOption.builder().user_access_token(user_access_token).build()
    response: MoveDocsToWikiSpaceNodeResponse = client.wiki.v2.space_node.move_docs_to_wiki(request, option)

    # 处理失败返回
    if not response.success():
        lark.logger.error(
            f"client.wiki.v2.space_node.move_docs_to_wiki failed, code: {response.code}, msg: {response.msg}, log_id: {response.get_log_id()}")
        return              
   

def main():
    parser = argparse.ArgumentParser(description='Copy file and update document blocks.')
    parser.add_argument('--user_token', type=str, required=True, help='User token for access')
    parser.add_argument('--product_name', type=str,required=True, help='Product name for copying')
    parser.add_argument('--folder_token', type=str,required=True, help='Folder token for copying')
    parser.add_argument('--space_id', type=str,required=True, help='Space id for moving')
    parser.add_argument('--version', type=str,required=True, help='Version number')
    args = parser.parse_args()
    
    client = initialize_client()
    file_token = "";
    request: ListFileRequest = ListFileRequest.builder() \
        .folder_token(args.folder_token) \
        .order_by("EditedTime") \
        .direction("DESC") \
        .build()

    # 发起请求
    option = lark.RequestOption.builder().user_access_token(args.user_token).build()
    response: ListFileResponse = client.drive.v1.file.list(request, option)

    # 处理失败返回
    if not response.success():
        lark.logger.error(
            f"client.drive.v1.file.list failed, code: {response.code}, msg: {response.msg}, log_id: {response.get_log_id()}")
        return
    
    file_token = None
    for file in response.data.files:
       if file.name == args.product_name:    
          file_token = file.token
          break
    if file_token is None:
       lark.logger.error(
            f"not support {args.product_name}")
       return 
       
    doc_id = copy_file(client, args.user_token, file_token, 'v'+args.version, args.folder_token)
	
    if doc_id is None:
        # Your business logic here
        pass
    else:
        update_all_document_blocks(client, doc_id, args.version, args.user_token)
        move_docs_to_wikispace(client, args.space_id, doc_id, args.user_token, args.product_name)

    
if __name__ == "__main__":
    main()
