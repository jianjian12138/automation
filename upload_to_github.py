import os
import requests
import base64

# GitHub配置
GITHUB_TOKEN = "YOUR_GITHUB_TOKEN"  # 需要替换为实际的GitHub Token
REPO_OWNER = "jianjian12138"
REPO_NAME = "automation"
BRANCH = "master"

# 项目目录
PROJECT_DIR = "f:\\JJ_test\\automation-test-platform"

# GitHub API基础URL
BASE_URL = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/contents"

# 获取文件内容并转换为base64
def get_file_content(file_path):
    with open(file_path, "rb") as f:
        content = f.read()
    return base64.b64encode(content).decode("utf-8")

# 上传文件到GitHub
def upload_file(file_path, relative_path):
    url = f"{BASE_URL}/{relative_path}"
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    # 检查文件是否已存在
    response = requests.get(url, headers=headers)
    
    content = get_file_content(file_path)
    
    data = {
        "message": f"Update {relative_path}",
        "content": content,
        "branch": BRANCH
    }
    
    if response.status_code == 200:
        # 文件已存在，需要获取sha值进行更新
        data["sha"] = response.json()["sha"]
        response = requests.put(url, headers=headers, json=data)
    else:
        # 文件不存在，创建新文件
        response = requests.put(url, headers=headers, json=data)
    
    return response.status_code, response.json()

# 递归遍历目录并上传文件
def upload_directory(directory, base_path=""):
    for root, dirs, files in os.walk(directory):
        for file in files:
            file_path = os.path.join(root, file)
            relative_path = os.path.relpath(file_path, PROJECT_DIR)
            # 将Windows路径分隔符转换为Unix格式
            relative_path = relative_path.replace("\\", "/")
            
            print(f"上传文件: {relative_path}")
            status_code, result = upload_file(file_path, relative_path)
            
            if status_code in [200, 201]:
                print(f"✓ 成功: {relative_path}")
            else:
                print(f"✗ 失败: {relative_path}, 状态码: {status_code}, 错误: {result}")

if __name__ == "__main__":
    print("开始上传代码到GitHub...")
    upload_directory(PROJECT_DIR)
    print("上传完成！")